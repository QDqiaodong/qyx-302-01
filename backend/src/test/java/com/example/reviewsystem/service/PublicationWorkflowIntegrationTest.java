package com.example.reviewsystem.service;

import com.example.reviewsystem.entity.Dimensions;
import com.example.reviewsystem.entity.PublicationBatch;
import com.example.reviewsystem.entity.Score;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.exception.BusinessConflictException;
import com.example.reviewsystem.repository.ScoreRepository;
import com.example.reviewsystem.repository.WorkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实数据库（H2/MySQL 兼容模式）端到端：维度分实体映射、唯一约束、悲观锁 SQL、
 * 缺维停留待齐分、齐分即时出分、整批公示、已公示快照不被带走、未公示跟随新口径、
 * 统计平均排除半成品——全链路验证。
 */
@SpringBootTest(classes = PublicationWorkflowIntegrationTest.TestApp.class)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Transactional
class PublicationWorkflowIntegrationTest {

    // 测试引导类位于 service 包，需显式把扫描根设为 com.example.reviewsystem
    @org.springframework.boot.autoconfigure.SpringBootApplication(scanBasePackages = "com.example.reviewsystem")
    static class TestApp {
    }

    @Autowired PublicationService publicationService;
    @Autowired ScoreService scoreService;
    @Autowired ConfigService configService;
    @Autowired StatisticsService statisticsService;
    @Autowired WorkRepository workRepository;
    @Autowired ScoreRepository scoreRepository;

    private Work createApprovedWork(String name) {
        return workRepository.save(Work.builder()
                .workName(name).category("绘画").creatorName(name + "作者")
                .status("APPROVED").published(false).isQualified(false).build());
    }

    /** 一次提交四维分（同一评委）。 */
    private void submitAllDimensions(long workId, long judgeId, String judgeName,
                                    String c, String comp, String comm, String craft) {
        scoreService.submitScores(workId, judgeId, judgeName, Map.of(
                Dimensions.CREATIVITY, new BigDecimal(c),
                Dimensions.COMPLETION, new BigDecimal(comp),
                Dimensions.COMMERCIAL_POTENTIAL, new BigDecimal(comm),
                Dimensions.CRAFTSMANSHIP, new BigDecimal(craft)));
    }

    private void setWeights(String c, String comp, String comm, String craft) {
        configService.updateWeights(new java.util.HashMap<>() {{
            put(Dimensions.CREATIVITY, new BigDecimal(c));
            put(Dimensions.COMPLETION, new BigDecimal(comp));
            put(Dimensions.COMMERCIAL_POTENTIAL, new BigDecimal(comm));
            put(Dimensions.CRAFTSMANSHIP, new BigDecimal(craft));
        }});
    }

    @Test
    void fullWorkflow_publishedFrozen_unpublishedLiveRescoreRejected() {
        Work w1 = createApprovedWork("作品甲");
        Work w2 = createApprovedWork("作品乙");

        // 默认权重 0.30/0.25/0.25/0.20，合格线 60
        submitAllDimensions(w1.getId(), 1, "评委一", "95", "60", "60", "60");
        submitAllDimensions(w2.getId(), 1, "评委一", "95", "60", "60", "60");

        Work w1Graded = workRepository.findById(w1.getId()).orElseThrow();
        Work w2Graded = workRepository.findById(w2.getId()).orElseThrow();
        assertEquals("GRADED", w1Graded.getStatus());
        // 95*0.30 + 60*0.70 = 70.50
        assertEquals(new BigDecimal("70.50"), w1Graded.getTotalScore());
        assertFalse(w1Graded.getPublished());

        // 秘书改一次配置（未公示作品应跟随）：合格线抬到 75
        configService.updateThreshold(com.example.reviewsystem.entity.ThresholdConfig.builder()
                .qualifiedScore(new BigDecimal("75")).extremeThreshold(new BigDecimal("20")).build());
        w2Graded = workRepository.findById(w2.getId()).orElseThrow();
        assertFalse(w2Graded.getIsQualified(), "70.5 < 新合格线 75 → 不合格");

        // 把合格线改回 60，再公示作品甲（批次按当前口径锁快照）
        configService.updateThreshold(com.example.reviewsystem.entity.ThresholdConfig.builder()
                .qualifiedScore(new BigDecimal("60")).extremeThreshold(new BigDecimal("20")).build());
        PublicationBatch batch = publicationService.publish("首批", List.of(w1.getId()));
        assertNotNull(batch.getId());
        assertEquals(new BigDecimal("0.30"), batch.getCreativityWeight());
        assertEquals(new BigDecimal("60"), batch.getQualifiedScore());

        w1Graded = workRepository.findById(w1.getId()).orElseThrow();
        assertTrue(w1Graded.getPublished());
        assertEquals(batch.getId(), w1Graded.getPublicationBatchId());
        assertEquals(new BigDecimal("70.50"), w1Graded.getPublishedTotalScore());
        assertEquals("B", w1Graded.getPublishedGrade());
        assertTrue(w1Graded.getPublishedIsQualified());
        assertNotNull(w1Graded.getPublishedAt());

        // 公示后秘书再改权重（工艺权重 0.70）+ 合格线 80
        setWeights("0.10", "0.10", "0.10", "0.70");
        configService.updateThreshold(com.example.reviewsystem.entity.ThresholdConfig.builder()
                .qualifiedScore(new BigDecimal("80")).extremeThreshold(new BigDecimal("20")).build());

        // 已公示作品：分数/等级/合格性纹丝不动，仍是公示快照
        w1Graded = workRepository.findById(w1.getId()).orElseThrow();
        assertEquals(new BigDecimal("70.50"), w1Graded.getTotalScore());
        assertEquals("B", w1Graded.getGrade());
        assertTrue(w1Graded.getIsQualified());
        assertEquals(new BigDecimal("70.50"), w1Graded.getPublishedTotalScore());
        assertEquals(batch.getId(), w1Graded.getPublicationBatchId());

        // 未公示作品：被新口径重算。95*0.10 + 60*0.10 + 60*0.10 + 60*0.70 = 63.50 → B，合格线 80 → 不合格
        w2Graded = workRepository.findById(w2.getId()).orElseThrow();
        assertFalse(w2Graded.getPublished());
        assertEquals(new BigDecimal("63.50"), w2Graded.getTotalScore());
        assertEquals("B", w2Graded.getGrade());
        assertFalse(w2Graded.getIsQualified());

        // 评委再给已公示作品补维度分 → 拒绝，且分数/快照不变
        BusinessConflictException ex = assertThrows(BusinessConflictException.class,
                () -> scoreService.submitScores(w1.getId(), 1L, "评委一",
                        Map.of(Dimensions.CRAFTSMANSHIP, new BigDecimal("10"))));
        assertTrue(ex.getMessage().contains("公示"));

        w1Graded = workRepository.findById(w1.getId()).orElseThrow();
        assertEquals(new BigDecimal("70.50"), w1Graded.getTotalScore(), "详情仍展示公示快照");

        // 第二位评委抢未公示作品已经有人打过的维度（创意）→ 409，该维只留一条有效分
        BusinessConflictException race = assertThrows(BusinessConflictException.class,
                () -> scoreService.submitScores(w2.getId(), 2L, "评委二",
                        Map.of(Dimensions.CREATIVITY, new BigDecimal("100"))));
        assertTrue(race.getMessage().contains("创意"), "要告诉第二位评委创意已有人写过");
        Score creativityRow = scoreRepository.findByWorkIdAndDimension(
                w2.getId(), Dimensions.CREATIVITY).orElseThrow();
        assertEquals(1L, creativityRow.getJudgeId(), "创意有效分仍是评委一的，第二位评委不得覆盖");
        assertEquals(new BigDecimal("95"), creativityRow.getValue());

        // 不能重复公示已公示作品（整批拒绝）
        assertThrows(BusinessConflictException.class,
                () -> publicationService.publish("重复批", List.of(w1.getId())));

        // 整批撤回：作品甲回到未公示，并按当前新口径恢复即时分
        PublicationBatch revoked = publicationService.revokeBatch(batch.getId());
        assertEquals("REVOKED", revoked.getStatus());
        w1Graded = workRepository.findById(w1.getId()).orElseThrow();
        assertFalse(w1Graded.getPublished());
        assertNull(w1Graded.getPublicationBatchId());
        assertNull(w1Graded.getPublishedTotalScore());
        // 撤回后按当前口径：95*.1 + 60*.1 + 60*.1 + 60*.7 = 63.50
        assertEquals(new BigDecimal("63.50"), w1Graded.getTotalScore());
    }

    @Test
    void partialDimensions_holdAtPendingScores_andNeverEnterGradeOrStatistics() {
        Work partial = createApprovedWork("只交创意");
        Work complete = createApprovedWork("四维齐全");

        // 只交创意一维
        scoreService.submitScores(partial.getId(), 1L, "评委一",
                Map.of(Dimensions.CREATIVITY, new BigDecimal("20")));

        Work partialReloaded = workRepository.findById(partial.getId()).orElseThrow();
        assertEquals("PENDING_SCORES", partialReloaded.getStatus(), "缺三维必须停在待齐分");
        assertNull(partialReloaded.getTotalScore(), "综合分绝不能用缺维当 0 分垫出来");
        assertNull(partialReloaded.getGrade());
        assertFalse(partialReloaded.getIsQualified());

        // 另一件四维齐全
        submitAllDimensions(complete.getId(), 1, "评委一", "90", "90", "90", "90");

        // 等级分布只含齐分作品：半成品不进饼图（不会被零分垫成 C 级）
        Map<String, Object> gradeDist = statisticsService.getGradeDistribution();
        assertEquals(1L, gradeDist.get("total"));

        // 维度平均只吃齐分作品的维度分（90），半成品的创意 20 分不得把平均拉低
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> dimAvgs = (Map<String, BigDecimal>)
                statisticsService.getDimensionDistribution().get("dimensionAverages");
        assertNotNull(dimAvgs);
        assertEquals(0, dimAvgs.get(Dimensions.CREATIVITY).compareTo(new BigDecimal("90")),
                "创意平均只能来自齐分作品，半成品 20 分不得计入");

        // 落选名单不含半成品
        assertTrue(statisticsService.getFailedWorks().stream()
                .noneMatch(w -> w.getId().equals(partial.getId())));

        // 半成品不允许公示（总监口径：齐分才能进综合分/公示）
        assertThrows(RuntimeException.class,
                () -> publicationService.publish("半成品批次", List.of(partial.getId())));
        assertFalse(workRepository.findById(partial.getId()).orElseThrow().getPublished());

        // 补齐剩余三维后才进入综合分
        scoreService.submitScores(partial.getId(), 2L, "评委二", Map.of(
                Dimensions.COMPLETION, new BigDecimal("80"),
                Dimensions.COMMERCIAL_POTENTIAL, new BigDecimal("70"),
                Dimensions.CRAFTSMANSHIP, new BigDecimal("60")));
        Work nowGraded = workRepository.findById(partial.getId()).orElseThrow();
        assertEquals("GRADED", nowGraded.getStatus());
        assertNotNull(nowGraded.getTotalScore());
    }

    @Test
    void atomicSubmission_failureLeavesNoHalfDimensions() {
        Work work = createApprovedWork("原子性作品");
        // 创意一维先落库
        scoreService.submitScores(work.getId(), 1L, "评委一",
                Map.of(Dimensions.CREATIVITY, new BigDecimal("88")));
        assertEquals("PENDING_SCORES", workRepository.findById(work.getId()).orElseThrow().getStatus());

        // 同一提交里创意撞车：整单回滚，同单的完成度也不得留下
        assertThrows(BusinessConflictException.class,
                () -> scoreService.submitScores(work.getId(), 3L, "评委三", Map.of(
                        Dimensions.CREATIVITY, new BigDecimal("50"),
                        Dimensions.COMPLETION, new BigDecimal("50"))));

        List<Score> rows = scoreRepository.findByWorkId(work.getId());
        assertEquals(1, rows.size(), "失败的提交不得留下半截维度");
        assertEquals(Dimensions.CREATIVITY, rows.get(0).getDimension());
        assertEquals(new BigDecimal("88"), rows.get(0).getValue());
        // 作品仍是只缺不余的待齐分状态
        assertEquals("PENDING_SCORES", workRepository.findById(work.getId()).orElseThrow().getStatus());
    }

    @Test
    void deletingLastDimension_clearsGradeAndReturnsToPendingScores() {
        Work work = createApprovedWork("删回待齐分");
        submitAllDimensions(work.getId(), 1, "评委一", "70", "70", "70", "70");
        assertEquals("GRADED", workRepository.findById(work.getId()).orElseThrow().getStatus());

        Score craftsmanship = scoreRepository.findByWorkIdAndDimension(
                work.getId(), Dimensions.CRAFTSMANSHIP).orElseThrow();
        scoreService.deleteScore(craftsmanship.getId());

        Work reloaded = workRepository.findById(work.getId()).orElseThrow();
        assertEquals("PENDING_SCORES", reloaded.getStatus());
        assertNull(reloaded.getTotalScore(), "删缺一维后旧综合分必须立即清空，不得悬挂");
        assertNull(reloaded.getGrade());

        // 删掉全部维度 → 回到 APPROVED
        List<Score> remaining = new java.util.ArrayList<>(scoreRepository.findByWorkId(work.getId()));
        remaining.forEach(s -> scoreService.deleteScore(s.getId()));
        Work approvedAgain = workRepository.findById(work.getId()).orElseThrow();
        assertEquals("APPROVED", approvedAgain.getStatus());
        assertNull(approvedAgain.getTotalScore());
    }

    @Test
    void publishFailsWholeBatch_whenOneWorkNotReady() {
        Work ready = createApprovedWork("就绪");
        Work notReady = createApprovedWork("未打分");
        submitAllDimensions(ready.getId(), 1, "评委一", "90", "90", "90", "90");

        // 一件就绪、一件连分都没有 → 整批拒绝，就绪作品必须仍未公示（无半成功）
        assertThrows(RuntimeException.class,
                () -> publicationService.publish("坏批次", List.of(ready.getId(), notReady.getId())));

        Work readyReloaded = workRepository.findById(ready.getId()).orElseThrow();
        assertFalse(readyReloaded.getPublished());
        assertNull(readyReloaded.getPublicationBatchId());
        Work notReadyReloaded = workRepository.findById(notReady.getId()).orElseThrow();
        assertFalse(notReadyReloaded.getPublished());
    }
}
