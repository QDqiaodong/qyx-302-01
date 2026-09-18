package com.example.reviewsystem.service;

import com.example.reviewsystem.dto.response.ExhibitionBoardItem;
import com.example.reviewsystem.entity.Handover;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.exception.BusinessConflictException;
import com.example.reviewsystem.exception.ResourceNotFoundException;
import com.example.reviewsystem.repository.HandoverRepository;
import com.example.reviewsystem.repository.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 展陈看板与参展凭证（展厅主任口径）：
 * 只有点交完成的作品才能上墙、才能领参展凭证；点交没完成的作品
 * 看板上一律没有位置——不是标灰提示，而是根本不出现。
 */
@Service
@RequiredArgsConstructor
public class ExhibitionService {

    public static final String CERT_NOT_ISSUED = "NOT_ISSUED";
    public static final String CERT_ISSUED = "ISSUED";
    public static final String CERT_REVOKED = "REVOKED";

    private final WorkRepository workRepository;
    private final HandoverRepository handoverRepository;

    /**
     * 展陈看板：只取点交状态为已点交的作品，且必须能对上一张已完成的点交单。
     * 未点交 / 点交中的作品连行都不会返回，参观者不会看到没到场的展品。
     */
    @Transactional(readOnly = true)
    public List<ExhibitionBoardItem> getBoard() {
        List<Work> works = workRepository.findByHandoverStatus(HandoverService.STATUS_COMPLETED);
        if (works.isEmpty()) {
            return List.of();
        }
        List<Long> workIds = works.stream().map(Work::getId).toList();
        Map<Long, Handover> handoverByWorkId = handoverRepository.findByWorkIdIn(workIds).stream()
                .collect(Collectors.toMap(Handover::getWorkId, Function.identity()));

        List<ExhibitionBoardItem> items = new ArrayList<>();
        for (Work work : works) {
            Handover handover = handoverByWorkId.get(work.getId());
            // 防御：状态与点交单对不上（单缺失或未完成）的作品不上墙
            if (handover == null || !HandoverService.STATUS_COMPLETED.equals(handover.getStatus())) {
                continue;
            }
            items.add(ExhibitionBoardItem.builder()
                    .workId(work.getId())
                    .workName(work.getWorkName())
                    .category(work.getCategory())
                    .creatorName(work.getCreatorName())
                    .pieceCount(handover.getPieceCountSnapshot() != null
                            ? handover.getPieceCountSnapshot() : work.getPieceCount())
                    .conditionStatus(handover.getConditionStatus())
                    .receiver(handover.getReceiver())
                    .handoverCompletedAt(handover.getCompletedAt())
                    .certificateStatus(work.getCertificateStatus())
                    .certificateNo(work.getCertificateNo())
                    .build());
        }
        items.sort(Comparator.comparing(ExhibitionBoardItem::getHandoverCompletedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return items;
    }

    /**
     * 发放参展凭证：只有已点交的作品能领。点交未完成一律拒绝（409），
     * 秘书不能先把凭证发给实物还没到场的作者。
     */
    @Transactional
    public Work issueCertificate(Long workId) {
        Work work = workRepository.findByIdForUpdate(workId)
                .orElseThrow(() -> new ResourceNotFoundException("Work not found with id: " + workId));

        if (!HandoverService.STATUS_COMPLETED.equals(work.getHandoverStatus())) {
            throw new BusinessConflictException("作品《" + work.getWorkName() + "》点交未完成（当前："
                    + HandoverService.statusLabel(work.getHandoverStatus())
                    + "），参展凭证不能发放——实物到厅并完成点交后才能领取");
        }
        if (CERT_ISSUED.equals(work.getCertificateStatus())) {
            // 已发放：幂等返回，不重复出证
            return work;
        }

        work.setCertificateStatus(CERT_ISSUED);
        work.setCertificateNo("CZ-" + work.getId() + "-"
                + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        work.setCertificateIssuedAt(LocalDateTime.now());
        work.setCertificateRevokedAt(null);
        return workRepository.save(work);
    }

}
