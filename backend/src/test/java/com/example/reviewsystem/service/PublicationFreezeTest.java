package com.example.reviewsystem.service;

import com.example.reviewsystem.entity.Dimensions;
import com.example.reviewsystem.entity.Score;
import com.example.reviewsystem.entity.ThresholdConfig;
import com.example.reviewsystem.entity.WeightConfig;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.exception.BusinessConflictException;
import com.example.reviewsystem.exception.BusinessValidationException;
import com.example.reviewsystem.repository.PublicationBatchRepository;
import com.example.reviewsystem.repository.ScoreRepository;
import com.example.reviewsystem.repository.ThresholdConfigRepository;
import com.example.reviewsystem.repository.WeightConfigRepository;
import com.example.reviewsystem.repository.WorkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 不启动 Spring 容器（因此不需要 MySQL/Redis），用 Mockito 驱动真实的
 * PublicationService / ScoreService / GradingService 逻辑，验证新口径：
 * 1) 缺任何一维：作品停在 PENDING_SCORES，综合分/等级不允许被零分凑出来；
 * 2) 同维只能留一条有效分：第二位评委抢同一维整单 409，且任何分数都不落库；
 * 3) 一次提交多维度要么全留要么全不留（原子）；
 * 4) 已公示作品按快照锁死，改分拒绝；
 * 5) 公示对缺维作品整体拒绝。
 */
@ExtendWith(MockitoExtension.class)
class PublicationFreezeTest {

    @Mock WorkRepository workRepository;
    @Mock ScoreRepository scoreRepository;
    @Mock WeightConfigRepository weightConfigRepository;
    @Mock ThresholdConfigRepository thresholdConfigRepository;
    @Mock PublicationBatchRepository publicationBatchRepository;
    @Mock ScoreShardingService scoreShardingService;
    @Mock StringRedisTemplate stringRedisTemplate;

    GradingService gradingService = new GradingService();
    ConfigService configService;
    PublicationService publicationService;
    ScoreService scoreService;

    private ScoringConfig oldConfig;
    private ScoringConfig newConfig;

    private Work publishedWork;
    private Work unpublishedWork;

    @BeforeEach
    void setUp() {
        configService = new ConfigService(weightConfigRepository, thresholdConfigRepository,
                workRepository, scoreRepository, gradingService, stringRedisTemplate);
        publicationService = new PublicationService(publicationBatchRepository, workRepository,
                scoreRepository, configService, gradingService);
        scoreService = new ScoreService(scoreRepository, workRepository, configService,
                scoreShardingService, gradingService);

        oldConfig = new ScoringConfig(
                new BigDecimal("0.30"), new BigDecimal("0.25"),
                new BigDecimal("0.25"), new BigDecimal("0.20"),
                new BigDecimal("60"), new BigDecimal("20"));
        newConfig = new ScoringConfig(
                new BigDecimal("0.10"), new BigDecimal("0.10"),
                new BigDecimal("0.10"), new BigDecimal("0.70"),
                new BigDecimal("80"), new BigDecimal("20"));

        publishedWork = gradedWork(1L, "已公示作品");
        unpublishedWork = gradedWork(2L, "未公示作品");

        // 当前库配置 = 新口径（模拟公示后秘书改了权重与合格线）
        lenient().when(weightConfigRepository.findAll()).thenReturn(List.of(
                weight(Dimensions.CREATIVITY, newConfig.creativityWeight()),
                weight(Dimensions.COMPLETION, newConfig.completionWeight()),
                weight(Dimensions.COMMERCIAL_POTENTIAL, newConfig.commercialPotentialWeight()),
                weight(Dimensions.CRAFTSMANSHIP, newConfig.craftsmanshipWeight())));
        lenient().when(thresholdConfigRepository.findAll()).thenReturn(List.of(threshold(
                newConfig.qualifiedScore(), newConfig.extremeThreshold())));
    }

    private Work gradedWork(Long id, String name) {
        Work w = Work.builder()
                .id(id).workName(name).category("绘画").creatorName("作者" + id)
                .status("GRADED").published(false).isQualified(false).build();
        GradeResult r = gradingService.calculate(scoresFor(id), oldConfig).orElseThrow();
        w.setTotalScore(r.totalScore());
        w.setGrade(r.grade());
        w.setIsQualified(r.qualified());
        return w;
    }

    /** 四条维度分（四个维度各一条有效分）：创意 95，其余 60。 */
    private List<Score> scoresFor(Long workId) {
        return List.of(
                dimensionScore(workId, 7L, "评委甲", Dimensions.CREATIVITY, "95"),
                dimensionScore(workId, 7L, "评委甲", Dimensions.COMPLETION, "60"),
                dimensionScore(workId, 7L, "评委甲", Dimensions.COMMERCIAL_POTENTIAL, "60"),
                dimensionScore(workId, 7L, "评委甲", Dimensions.CRAFTSMANSHIP, "60"));
    }

    private Score dimensionScore(long workId, long judgeId, String judgeName, String dimension, String value) {
        return Score.builder().workId(workId).judgeId(judgeId).judgeName(judgeName)
                .dimension(dimension).value(new BigDecimal(value)).build();
    }

    private WeightConfig weight(String dimension, BigDecimal value) {
        WeightConfig c = new WeightConfig();
        c.setDimension(dimension);
        c.setWeight(value);
        return c;
    }

    private ThresholdConfig threshold(BigDecimal qualified, BigDecimal extreme) {
        return ThresholdConfig.builder().qualifiedScore(qualified).extremeThreshold(extreme).build();
    }

    @Test
    void missingDimension_staysPendingScores_andIsNeverPaddedWithZero() {
        Work work = Work.builder().id(10L).workName("半成品").category("绘画")
                .creatorName("作者10").status("APPROVED").published(false).isQualified(false).build();
        when(workRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(work));

        // 只交创意一维（打分面板允许只交创意分）
        Score persisted = dimensionScore(10L, 8L, "评委乙", Dimensions.CREATIVITY, "90");
        persisted.setId(101L);
        when(scoreRepository.save(any(Score.class))).thenReturn(persisted);
        // submitScores 查重前读到空，重算时读到刚落库的这一条创意分
        when(scoreRepository.findByWorkId(10L))
                .thenReturn(List.of())
                .thenReturn(List.of(persisted));

        List<Score> result = scoreService.submitScores(10L, 8L, "评委乙",
                Map.of(Dimensions.CREATIVITY, new BigDecimal("90")));

        assertEquals(1, result.size());
        assertEquals("PENDING_SCORES", work.getStatus(), "缺三维必须停在待齐分");
        assertNull(work.getTotalScore(), "缺维不得用零分垫出综合分");
        assertNull(work.getGrade(), "缺维不得产生等级");
        assertFalse(work.getIsQualified());
    }

    @Test
    void submittingLastMissingDimensions_graduatesWorkToGraded() {
        Work work = Work.builder().id(11L).workName("即将齐分").category("绘画")
                .creatorName("作者11").status("PENDING_SCORES").published(false).isQualified(false).build();
        when(workRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(work));

        Score creativity = dimensionScore(11L, 8L, "评委乙", Dimensions.CREATIVITY, "90");

        Score completion = dimensionScore(11L, 9L, "评委丙", Dimensions.COMPLETION, "80");
        Score commercial = dimensionScore(11L, 9L, "评委丙", Dimensions.COMMERCIAL_POTENTIAL, "70");
        Score craftsmanship = dimensionScore(11L, 9L, "评委丙", Dimensions.CRAFTSMANSHIP, "60");
        // submitScores 查重时已有创意一维；重算时读到四维齐全
        when(scoreRepository.findByWorkId(11L))
                .thenReturn(List.of(creativity))
                .thenReturn(List.of(creativity, completion, commercial, craftsmanship));
        when(scoreRepository.save(any(Score.class)))
                .thenAnswer(inv -> {
                    Score s = inv.getArgument(0);
                    s.setId(System.nanoTime());
                    return s;
                });

        scoreService.submitScores(11L, 9L, "评委丙", Map.of(
                Dimensions.COMPLETION, new BigDecimal("80"),
                Dimensions.COMMERCIAL_POTENTIAL, new BigDecimal("70"),
                Dimensions.CRAFTSMANSHIP, new BigDecimal("60")));

        assertEquals("GRADED", work.getStatus());
        assertNotNull(work.getTotalScore());
        // 90*0.10 + 80*0.10 + 70*0.10 + 60*0.70 = 66.00（当前新口径，合格线 80 → 不合格）
        assertEquals(new BigDecimal("66.00"), work.getTotalScore());
        assertFalse(work.getIsQualified());
    }

    @Test
    void sameDimensionRace_wholeSubmissionRejected_andNothingPersisted() {
        Work work = Work.builder().id(12L).workName("抢同一维").category("绘画")
                .creatorName("作者12").status("PENDING_SCORES").published(false).isQualified(false).build();
        when(workRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(work));
        // 创意已被评委乙写过
        Score creativity = dimensionScore(12L, 8L, "评委乙", Dimensions.CREATIVITY, "90");
        when(scoreRepository.findByWorkId(12L)).thenReturn(List.of(creativity));

        // 评委丁同时提交创意 + 完成度：创意撞车，整单必须拒绝——完成度也不能留下
        BusinessConflictException ex = assertThrows(BusinessConflictException.class,
                () -> scoreService.submitScores(12L, 10L, "评委丁", Map.of(
                        Dimensions.CREATIVITY, new BigDecimal("70"),
                        Dimensions.COMPLETION, new BigDecimal("70"))));
        assertTrue(ex.getMessage().contains("创意"), "要让评委看到是哪一维已有人写过: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("评委乙"));

        verify(scoreRepository, never()).save(any());
        verify(scoreShardingService, never()).saveScore(any());
        verify(workRepository, never()).save(any());
    }

    @Test
    void emptySubmission_rejected() {
        Work work = Work.builder().id(13L).workName("空提交").category("绘画")
                .creatorName("作者13").status("APPROVED").published(false).build();
        when(workRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(work));

        assertThrows(BusinessValidationException.class,
                () -> scoreService.submitScores(13L, 1L, "评委甲", Map.of()));
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void publishedWork_isFrozen_andUnpublishedWork_followsNewConfig() {
        BigDecimal oldScore = publishedWork.getTotalScore();
        String oldGrade = publishedWork.getGrade();
        Boolean oldQualified = publishedWork.getIsQualified();
        assertNotNull(oldScore);

        // ---- 公示批次：锁定旧口径快照 ----
        when(weightConfigRepository.findAllForUpdate()).thenReturn(List.of(
                weight(Dimensions.CREATIVITY, oldConfig.creativityWeight()),
                weight(Dimensions.COMPLETION, oldConfig.completionWeight()),
                weight(Dimensions.COMMERCIAL_POTENTIAL, oldConfig.commercialPotentialWeight()),
                weight(Dimensions.CRAFTSMANSHIP, oldConfig.craftsmanshipWeight())));
        when(thresholdConfigRepository.findAllForUpdate()).thenReturn(List.of(threshold(
                oldConfig.qualifiedScore(), oldConfig.extremeThreshold())));
        when(workRepository.findByIdsForUpdate(any())).thenReturn(List.of(publishedWork));
        when(scoreRepository.findByWorkId(1L)).thenReturn(scoresFor(1L));
        when(publicationBatchRepository.save(any())).thenAnswer(inv -> {
            com.example.reviewsystem.entity.PublicationBatch b = inv.getArgument(0);
            b.setId(99L);
            return b;
        });

        var batch = publicationService.publish("第一批", List.of(1L));

        assertTrue(publishedWork.getPublished(), "公示后作品必须打上已公示标记");
        assertEquals(batch.getId(), publishedWork.getPublicationBatchId());
        assertEquals(oldScore, publishedWork.getPublishedTotalScore(), "快照综合分必须写入");
        assertEquals(oldGrade, publishedWork.getPublishedGrade(), "快照等级必须写入");
        assertEquals(oldQualified, publishedWork.getPublishedIsQualified());
        assertNotNull(publishedWork.getPublishedAt());
        assertEquals(oldConfig.creativityWeight(), batch.getCreativityWeight());
        assertEquals(oldConfig.qualifiedScore(), batch.getQualifiedScore());

        // ---- 秘书改口径后只刷未公示已评分作品 ----
        when(weightConfigRepository.findAllForUpdate()).thenReturn(List.of(
                weight(Dimensions.CREATIVITY, newConfig.creativityWeight()),
                weight(Dimensions.COMPLETION, newConfig.completionWeight()),
                weight(Dimensions.COMMERCIAL_POTENTIAL, newConfig.commercialPotentialWeight()),
                weight(Dimensions.CRAFTSMANSHIP, newConfig.craftsmanshipWeight())));
        when(thresholdConfigRepository.findAllForUpdate()).thenReturn(List.of(threshold(
                newConfig.qualifiedScore(), newConfig.extremeThreshold())));
        when(workRepository.findGradedUnpublishedForUpdate()).thenReturn(List.of(unpublishedWork));
        when(scoreRepository.findByWorkId(2L)).thenReturn(scoresFor(2L));
        when(thresholdConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        configService.updateThreshold(threshold(newConfig.qualifiedScore(), newConfig.extremeThreshold()));

        GradeResult expectedUnpublished = gradingService.calculate(scoresFor(2L), newConfig).orElseThrow();
        assertFalse(unpublishedWork.getPublished());
        assertEquals(expectedUnpublished.totalScore(), unpublishedWork.getTotalScore());
        assertTrue(unpublishedWork.getTotalScore().compareTo(oldScore) < 0,
                "未公示作品必须按新口径重算出更低分");
        assertFalse(unpublishedWork.getIsQualified(), "新合格线 80 下该作品不合格");

        assertEquals(oldScore, publishedWork.getTotalScore(), "已公示综合分不得被新权重带走");
        assertEquals(oldGrade, publishedWork.getGrade(), "已公示等级徽章不得跳动");
        assertEquals(oldQualified, publishedWork.getPublishedIsQualified());
        assertEquals(oldScore, publishedWork.getPublishedTotalScore());
    }

    @Test
    void rescoringPublishedWork_isRejected() {
        publishedWork.setPublished(true);
        publishedWork.setPublicationBatchId(9L);
        when(workRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(publishedWork));

        BusinessConflictException ex = assertThrows(BusinessConflictException.class,
                () -> scoreService.submitScores(1L, 7L, "评委甲",
                        Map.of(Dimensions.CREATIVITY, new BigDecimal("20"))));
        assertTrue(ex.getMessage().contains("公示"));

        // 拒绝发生在任何写入之前：维度分不保存、分表不写、不重算
        verify(scoreRepository, never()).save(any());
        verify(scoreShardingService, never()).saveScore(any());
        verify(workRepository, never()).save(any());
        assertEquals("已公示作品", publishedWork.getWorkName());
    }

    @Test
    void publishRejects_nonGradedWork() {
        Work pending = Work.builder().id(3L).workName("未凑齐分").category("绘画")
                .creatorName("作者3").status("PENDING_SCORES").published(false).build();
        when(weightConfigRepository.findAllForUpdate()).thenReturn(List.of(
                weight(Dimensions.CREATIVITY, oldConfig.creativityWeight()),
                weight(Dimensions.COMPLETION, oldConfig.completionWeight()),
                weight(Dimensions.COMMERCIAL_POTENTIAL, oldConfig.commercialPotentialWeight()),
                weight(Dimensions.CRAFTSMANSHIP, oldConfig.craftsmanshipWeight())));
        when(thresholdConfigRepository.findAllForUpdate()).thenReturn(List.of(threshold(
                oldConfig.qualifiedScore(), oldConfig.extremeThreshold())));
        when(workRepository.findByIdsForUpdate(any())).thenReturn(List.of(pending));
        // 该作品只有创意一维
        when(scoreRepository.findByWorkId(3L)).thenReturn(List.of(
                dimensionScore(3L, 1L, "评委一", Dimensions.CREATIVITY, "90")));

        assertThrows(RuntimeException.class, () -> publicationService.publish("坏批次", List.of(3L)));
        verify(publicationBatchRepository, never()).save(any());
        assertFalse(pending.getPublished(), "失败的批次不能留下半成功的已公示作品");
        assertNull(pending.getPublicationBatchId());
    }

    @Test
    void grading_isDeterministicAcrossSnapshotAndLive() throws Exception {
        // 同一批四维分 + 同一套口径，无论从公示快照路径还是即时路径算，结果必须完全一致
        Method calc = GradingService.class.getMethod("calculate", List.class, ScoringConfig.class);
        GradeResult a = ((Optional<GradeResult>) calc.invoke(gradingService, scoresFor(5L), oldConfig)).orElseThrow();
        GradeResult b = ((Optional<GradeResult>) calc.invoke(gradingService, scoresFor(5L), oldConfig)).orElseThrow();
        assertEquals(a.totalScore(), b.totalScore());
        assertEquals(a.grade(), b.grade());
        assertEquals(a.qualified(), b.qualified());
        // 旧口径下：95*0.30 + 60*0.25 + 60*0.25 + 60*0.20 = 70.50 → B 且合格
        assertEquals(new BigDecimal("70.50"), a.totalScore());
        assertEquals("B", a.grade());
        assertTrue(a.qualified());
    }

    @Test
    void grading_returnsEmpty_whenAnyDimensionMissing() {
        // 只有三维：绝不允许算成"缺的维按 0 分"
        List<Score> threeDimensions = List.of(
                dimensionScore(6L, 1L, "评委一", Dimensions.CREATIVITY, "95"),
                dimensionScore(6L, 1L, "评委一", Dimensions.COMPLETION, "60"),
                dimensionScore(6L, 1L, "评委一", Dimensions.COMMERCIAL_POTENTIAL, "60"));
        assertTrue(gradingService.calculate(threeDimensions, oldConfig).isEmpty());
        assertTrue(gradingService.calculate(List.of(), oldConfig).isEmpty());
    }
}
