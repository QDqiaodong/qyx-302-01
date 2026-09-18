package com.example.reviewsystem.service;

import com.example.reviewsystem.entity.Handover;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.exception.BusinessConflictException;
import com.example.reviewsystem.exception.BusinessValidationException;
import com.example.reviewsystem.exception.ResourceNotFoundException;
import com.example.reviewsystem.repository.HandoverRepository;
import com.example.reviewsystem.repository.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 现场点交：作者把实物送到展厅时的交接登记。
 *
 * 状态机（只能逐步推进，失败整单作废）：
 *   未点交 NOT_STARTED --开始点交--> 点交中 IN_PROGRESS --三要素齐全--> 已点交 COMPLETED
 *   点交中 --点交失败--> 未点交（点交单整单删除，不留一半件数一半空着的半成品）
 *   已点交 --作者改掉件数--> 未点交（点交单作废、展墙撤下、参展凭证撤销，重新点交前不得上墙）
 *
 * 三要素：件数（作品档案上的件数）、完好情况、接收人——缺任何一样点交单不成立。
 */
@Service
@RequiredArgsConstructor
public class HandoverService {

    public static final String STATUS_NOT_STARTED = "NOT_STARTED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private final HandoverRepository handoverRepository;
    private final WorkRepository workRepository;

    @Transactional(readOnly = true)
    public Optional<Handover> getCurrentHandover(Long workId) {
        return handoverRepository.findByWorkId(workId);
    }

    /**
     * 开始点交：未点交 → 点交中。开出一张空点交单，现场逐项登记。
     */
    @Transactional
    public Handover startHandover(Long workId) {
        Work work = lockWork(workId);
        if (!STATUS_NOT_STARTED.equals(work.getHandoverStatus())) {
            throw new BusinessConflictException("作品《" + work.getWorkName() + "》当前点交状态为「"
                    + statusLabel(work.getHandoverStatus()) + "」，只有未点交的作品才能开始点交");
        }
        // 防御：清掉可能残留的旧单，保证一件作品同时只有一张点交单
        handoverRepository.findByWorkId(workId).ifPresent(handoverRepository::delete);

        Handover handover = Handover.builder()
                .workId(workId)
                .status(STATUS_IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();
        Handover saved = handoverRepository.save(handover);

        work.setHandoverStatus(STATUS_IN_PROGRESS);
        workRepository.save(work);
        return saved;
    }

    /**
     * 点交中登记内容：完好情况、接收人可以先记一半，完成点交时才校验三样齐全。
     */
    @Transactional
    public Handover updateDraft(Long workId, String conditionStatus, String receiver) {
        Work work = lockWork(workId);
        Handover handover = getInProgressHandover(work);
        handover.setConditionStatus(applyNullable(conditionStatus, handover.getConditionStatus()));
        handover.setReceiver(applyNullable(receiver, handover.getReceiver()));
        return handoverRepository.save(handover);
    }

    /**
     * 完成点交：点交中 → 已点交。三要素（件数、完好情况、接收人）缺一样整张点交单不成立，
     * 作品停在点交中，不上墙、不发凭证。完成后作品才允许进入展陈看板。
     */
    @Transactional
    public Handover completeHandover(Long workId, String conditionStatus, String receiver) {
        Work work = lockWork(workId);
        Handover handover = getInProgressHandover(work);

        // 允许在完成点交的同时补上最后缺的内容
        handover.setConditionStatus(applyNullable(conditionStatus, handover.getConditionStatus()));
        handover.setReceiver(applyNullable(receiver, handover.getReceiver()));

        List<String> missing = new ArrayList<>();
        if (work.getPieceCount() == null || work.getPieceCount() <= 0) {
            missing.add("件数（请先在作品档案中填好件数）");
        }
        if (handover.getConditionStatus() == null) {
            missing.add("完好情况");
        }
        if (handover.getReceiver() == null) {
            missing.add("接收人");
        }
        if (!missing.isEmpty()) {
            throw new BusinessValidationException(
                    "点交单不成立：还缺 " + String.join("、", missing) + "。件数、完好情况、接收人三样缺一不可完成点交");
        }

        handover.setStatus(STATUS_COMPLETED);
        handover.setPieceCountSnapshot(work.getPieceCount());
        handover.setCompletedAt(LocalDateTime.now());
        Handover saved = handoverRepository.save(handover);

        work.setHandoverStatus(STATUS_COMPLETED);
        workRepository.save(work);
        return saved;
    }

    /**
     * 点交失败：点交中 → 未点交。整张点交单作废删除——已登记的一半内容
     * （完好情况、接收人）一并清空，不允许一半件数留下、一半空着。
     */
    @Transactional
    public Work failHandover(Long workId) {
        Work work = lockWork(workId);
        if (!STATUS_IN_PROGRESS.equals(work.getHandoverStatus())) {
            throw new BusinessConflictException("作品《" + work.getWorkName() + "》当前点交状态为「"
                    + statusLabel(work.getHandoverStatus()) + "」，只有点交中的作品才能标记点交失败");
        }
        handoverRepository.findByWorkId(workId).ifPresent(handoverRepository::delete);
        work.setHandoverStatus(STATUS_NOT_STARTED);
        revokeCertificateIfIssued(work);
        return workRepository.save(work);
    }

    /**
     * 件数被改掉时调用（WorkService.updateWork 同事务内）：已点交作品的点交单立即作废，
     * 作品退回未点交——展陈看板只取已点交作品，状态一退即自动撤下展墙；
     * 已发放的参展凭证同步撤销。重新点交完成前不得再上墙、不得再领凭证。
     */
    @Transactional
    public void invalidateForPieceCountChange(Work work) {
        if (!STATUS_COMPLETED.equals(work.getHandoverStatus())) {
            return;
        }
        handoverRepository.findByWorkId(work.getId()).ifPresent(handoverRepository::delete);
        work.setHandoverStatus(STATUS_NOT_STARTED);
        revokeCertificateIfIssued(work);
    }

    private void revokeCertificateIfIssued(Work work) {
        if (ExhibitionService.CERT_ISSUED.equals(work.getCertificateStatus())) {
            work.setCertificateStatus(ExhibitionService.CERT_REVOKED);
            work.setCertificateRevokedAt(LocalDateTime.now());
        }
    }

    private Handover getInProgressHandover(Work work) {
        if (!STATUS_IN_PROGRESS.equals(work.getHandoverStatus())) {
            throw new BusinessConflictException("作品《" + work.getWorkName() + "》当前点交状态为「"
                    + statusLabel(work.getHandoverStatus())
                    + "」，点交必须按 未点交 → 点交中 → 已点交 逐步推进");
        }
        return handoverRepository.findByWorkId(work.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "作品点交状态为点交中但找不到点交单，数据异常: workId=" + work.getId()));
    }

    private Work lockWork(Long workId) {
        return workRepository.findByIdForUpdate(workId)
                .orElseThrow(() -> new ResourceNotFoundException("Work not found with id: " + workId));
    }

    /** null 表示不动这一样；空白串表示清空；其余按去空格后的内容登记。 */
    private String applyNullable(String incoming, String current) {
        if (incoming == null) {
            return current;
        }
        String trimmed = incoming.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case STATUS_IN_PROGRESS -> "点交中";
            case STATUS_COMPLETED -> "已点交";
            default -> "未点交";
        };
    }

}
