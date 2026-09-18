package com.example.reviewsystem.service;

import com.example.reviewsystem.entity.PublicationBatch;
import com.example.reviewsystem.entity.Score;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.exception.BusinessConflictException;
import com.example.reviewsystem.exception.BusinessValidationException;
import com.example.reviewsystem.exception.ResourceNotFoundException;
import com.example.reviewsystem.repository.PublicationBatchRepository;
import com.example.reviewsystem.repository.ScoreRepository;
import com.example.reviewsystem.repository.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对外公示：把一批已评分作品在同一个事务里、按同一时刻的配置快照整体锁定。
 *
 * 原子性保证：批次记录与所有作品的锁定都在一个事务内提交。
 * 中途断电 / 保存失败 → 事务回滚，作品全部保持未公示，不会出现"半批旧口径半批新口径"；
 * 秘书重试时看到的就是干净的未公示状态，可以整批重新发起。
 */
@Service
@RequiredArgsConstructor
public class PublicationService {

    private final PublicationBatchRepository publicationBatchRepository;
    private final WorkRepository workRepository;
    private final ScoreRepository scoreRepository;
    private final ConfigService configService;
    private final GradingService gradingService;

    @Transactional
    public PublicationBatch publish(String batchName, List<Long> workIds) {
        // 1) 先锁配置行并拍下同一刻的口径快照（与"保存配置"加锁顺序一致，避免交叉死锁）。
        //    整批共用这一份，之后配置怎么改都与本批无关。
        ScoringConfig config = configService.getCurrentScoringConfigForUpdate();

        // 2) 再锁定本次要公示的作品行，与评委改分、其他公示批次互斥。
        List<Work> works;
        if (workIds == null || workIds.isEmpty()) {
            works = workRepository.findGradedUnpublishedForUpdate();
        } else {
            List<Long> ids = workIds.stream().distinct().toList();
            works = workRepository.findByIdsForUpdate(ids);
            if (works.size() != ids.size()) {
                throw new BusinessValidationException("部分作品不存在，请刷新列表后重试");
            }
        }

        if (works.isEmpty()) {
            throw new BusinessValidationException("没有可公示的作品（需要已评分且未公示）");
        }

        for (Work work : works) {
            if (Boolean.TRUE.equals(work.getPublished())) {
                throw new BusinessConflictException(
                        "作品《" + work.getWorkName() + "》已在公示批次 #" + work.getPublicationBatchId() + "中，不能重复公示");
            }
            if (!"GRADED".equals(work.getStatus()) || work.getTotalScore() == null) {
                List<Score> presentScores = scoreRepository.findByWorkId(work.getId());
                List<String> missingLabels = ScoreService.missingDimensionLabels(presentScores);
                String hint = missingLabels.isEmpty()
                        ? "作品尚未评分"
                        : "还缺维度：" + String.join("、", missingLabels) + "（缺维作品停在待齐分，不能按零分凑进公示）";
                throw new BusinessValidationException(
                        "作品《" + work.getWorkName() + "》未凑齐四维评分，不能进入公示批次——" + hint);
            }
        }

        // 3) 建立批次（快照落库），先拿到批次 ID。
        LocalDateTime now = LocalDateTime.now();
        PublicationBatch batch = PublicationBatch.builder()
                .batchName(batchName)
                .status("PUBLISHED")
                .creativityWeight(config.creativityWeight())
                .completionWeight(config.completionWeight())
                .commercialPotentialWeight(config.commercialPotentialWeight())
                .craftsmanshipWeight(config.craftsmanshipWeight())
                .qualifiedScore(config.qualifiedScore())
                .extremeThreshold(config.extremeThreshold())
                .workCount(works.size())
                .publishedAt(now)
                .createdAt(now)
                .build();
        publicationBatchRepository.save(batch);

        // 4) 逐件按快照口径算分并钉进作品行：对外展示列与快照列同时写入，保证任何读路径看到的都是锁定值。
        for (Work work : works) {
            List<Score> scores = scoreRepository.findByWorkId(work.getId());
            // 进入循环的作品均已校验为 GRADED（四维齐全），这里缺维即数据异常，整批回滚。
            GradeResult result = gradingService.calculate(scores, config)
                    .orElseThrow(() -> new BusinessValidationException(
                            "作品《" + work.getWorkName() + "》四维标记齐全却算不出综合分，整批终止"));

            work.setTotalScore(result.totalScore());
            work.setGrade(result.grade());
            work.setIsQualified(result.qualified());

            work.setPublished(true);
            work.setPublicationBatchId(batch.getId());
            work.setPublishedTotalScore(result.totalScore());
            work.setPublishedGrade(result.grade());
            work.setPublishedIsQualified(result.qualified());
            work.setPublishedAt(now);
            workRepository.save(work);
        }

        return batch;
    }

    /**
     * 整批撤回：一个事务内把批次标记 REVOKED、作品全部回到未公示并按当前配置恢复即时分。
     * 与公示一样不允许半批残留。用于公示流程出错时"整批回到未公示"的重试路径。
     */
    @Transactional
    public PublicationBatch revokeBatch(Long batchId) {
        PublicationBatch batch = publicationBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("公示批次不存在: #" + batchId));
        if (!"PUBLISHED".equals(batch.getStatus())) {
            throw new BusinessConflictException("批次 #" + batchId + " 状态为 " + batch.getStatus() + "，不能撤回");
        }

        // 先锁配置行（与公示、保存配置同一加锁顺序），再锁批内作品，避免交叉死锁。
        ScoringConfig currentConfig = configService.getCurrentScoringConfigForUpdate();
        List<Work> works = workRepository.findByPublicationBatchIdForUpdate(batchId);
        if (works.isEmpty()) {
            throw new BusinessValidationException("批次 #" + batchId + " 下没有已锁定的作品，无法撤回");
        }

        for (Work work : works) {
            work.setPublished(false);
            work.setPublicationBatchId(null);
            work.setPublishedTotalScore(null);
            work.setPublishedGrade(null);
            work.setPublishedIsQualified(null);
            work.setPublishedAt(null);

            List<Score> scores = scoreRepository.findByWorkId(work.getId());
            if (scores.isEmpty()) {
                work.setStatus("APPROVED");
                work.setTotalScore(null);
                work.setGrade(null);
                work.setIsQualified(false);
            } else if (!ScoreService.missingDimensions(scores).isEmpty()) {
                // 撤回后发现维度被删过（理论上公示期改分会被拒，此处为防御）：退回待齐分，不按零分凑。
                work.setStatus("PENDING_SCORES");
                work.setTotalScore(null);
                work.setGrade(null);
                work.setIsQualified(false);
            } else {
                GradeResult result = gradingService.calculate(scores, currentConfig)
                        .orElseThrow(() -> new BusinessValidationException(
                                "撤回重算失败：作品《" + work.getWorkName() + "》四维齐全却算不出综合分"));
                work.setTotalScore(result.totalScore());
                work.setGrade(result.grade());
                work.setIsQualified(result.qualified());
                work.setStatus("GRADED");
            }
            workRepository.save(work);
        }

        batch.setStatus("REVOKED");
        batch.setWorkCount(works.size());
        return publicationBatchRepository.save(batch);
    }

    @Transactional(readOnly = true)
    public List<PublicationBatch> listBatches() {
        return publicationBatchRepository.findAllByOrderByPublishedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Work> listBatchWorks(Long batchId) {
        publicationBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("公示批次不存在: #" + batchId));
        return workRepository.findByPublicationBatchId(batchId);
    }
}
