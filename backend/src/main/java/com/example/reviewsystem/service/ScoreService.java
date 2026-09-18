package com.example.reviewsystem.service;

import com.example.reviewsystem.entity.Dimensions;
import com.example.reviewsystem.entity.Score;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.exception.BusinessConflictException;
import com.example.reviewsystem.exception.BusinessValidationException;
import com.example.reviewsystem.exception.ResourceNotFoundException;
import com.example.reviewsystem.repository.ScoreRepository;
import com.example.reviewsystem.repository.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final ScoreRepository scoreRepository;
    private final WorkRepository workRepository;
    private final ConfigService configService;
    private final ScoreShardingService scoreShardingService;
    private final GradingService gradingService;

    @Transactional(readOnly = true)
    public List<Score> getScoresByWorkId(Long workId) {
        return scoreRepository.findByWorkId(workId);
    }

    /**
     * 提交一次维度打分（允许只交一维或若干维）。
     *
     * 互斥：先对作品行加写锁，同一作品的两次提交严格串行。两位评委抢同一维度时，
     * 后到者会看到该维度已有有效分并被整单拒绝（409），已到者的分原样保留。
     * 原子：本单所有维度在同一事务内落库 + 重算作品状态；任何一步失败整体回滚，
     * 不会出现同一作品一半维度留下、一半空着的半成品。
     */
    @Transactional
    public List<Score> submitScores(Long workId, Long judgeId, String judgeName,
                                    Map<String, BigDecimal> requested) {
        Work work = getScoringWork(workId);
        validateJudge(judgeId, judgeName);
        Map<String, BigDecimal> dimensions = normalizeRequested(requested);
        if (dimensions.isEmpty()) {
            throw new BusinessValidationException("请至少提交一个维度的打分");
        }
        dimensions.forEach((dimension, value) -> validateDimensionValue(dimension, value));

        List<Score> existing = scoreRepository.findByWorkId(workId);

        // 抢同一维度：只能留下一条有效分，后到者必须看到这一维已有人写过。
        List<String> collided = new ArrayList<>();
        for (Score score : existing) {
            BigDecimal incoming = dimensions.get(score.getDimension());
            if (incoming != null) {
                collided.add(dimensionLabel(score.getDimension())
                        + "（已有 " + score.getJudgeName() + " 打出 " + score.getValue().stripTrailingZeros().toPlainString() + " 分）");
            }
        }
        if (!collided.isEmpty()) {
            throw new BusinessConflictException(
                    "以下维度已经有评委写过有效分，同一维度只保留一条：" + String.join("、", collided)
                            + "。请刷新后只补齐仍缺失的维度。");
        }

        List<Score> saved = new ArrayList<>();
        try {
            for (Map.Entry<String, BigDecimal> entry : dimensions.entrySet()) {
                Score score = Score.builder()
                        .workId(workId)
                        .judgeId(judgeId)
                        .judgeName(judgeName)
                        .dimension(entry.getKey())
                        .value(entry.getValue())
                        .build();
                Score persisted = scoreRepository.save(score);
                // 分表流水与主写入同一事务：主事务回滚时分表插入一并回滚，不留半截维度分。
                scoreShardingService.saveScore(persisted);
                saved.add(persisted);
            }
            // 触发约束校验（批量保存可能延迟 flush），确保并发抢维在此处被兜住并整体回滚。
            scoreRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 作品行锁是并发互斥的主防线；(work_id, dimension) 唯一约束是最终兜底：
            // 即使锁路径异常，同维也只能落一条，撞车方看到 409 而不是脏数据。
            throw new BusinessConflictException(
                    "提交的维度刚刚已被其他评委写入有效分（同一维度只保留一条），请刷新后只补齐仍缺失的维度");
        }

        recalculateWithinTransaction(work);
        return saved;
    }

    /**
     * 修改某条维度分（限该维度尚无他人有效分的场景——修改的是自己这条）。
     * 已公示作品在 getScoringWork 处即被拒绝；改分后按当前配置重算状态。
     */
    @Transactional
    public Score updateScore(Long id, BigDecimal value) {
        Score score = scoreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Score not found with id: " + id));
        Work work = getScoringWork(score.getWorkId());
        validateDimensionValue(score.getDimension(), value);

        score.setValue(value);
        Score saved = scoreRepository.save(score);
        recalculateWithinTransaction(work);
        return saved;
    }

    /**
     * 删除某条维度分。删完若作品退回缺维状态，综合分必须立即清空、状态退回待齐分，
     * 不允许删除后还挂着旧综合分。
     */
    @Transactional
    public void deleteScore(Long id) {
        Score score = scoreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Score not found with id: " + id));
        Work work = getScoringWork(score.getWorkId());
        scoreRepository.deleteById(id);
        scoreRepository.flush();
        recalculateWithinTransaction(work);
    }

    /**
     * 取待打分作品并校验状态：行锁内读取，必须存在、处于可打分阶段，且尚未对外公示。
     * 行锁保证与并发的公示事务互斥——公示一旦提交，改分请求只能看到 published=true 并被拒绝。
     */
    private Work getScoringWork(Long workId) {
        Work work = workRepository.findByIdForUpdate(workId)
                .orElseThrow(() -> new ResourceNotFoundException("Work not found with id: " + workId));

        if (Boolean.TRUE.equals(work.getPublished())) {
            throw new BusinessConflictException(
                    "作品已对外公示（批次 #" + work.getPublicationBatchId() + "），公示结果锁定，评委不能再修改打分");
        }

        if (!"APPROVED".equals(work.getStatus())
                && !"PENDING_SCORES".equals(work.getStatus())
                && !"GRADED".equals(work.getStatus())) {
            throw new BusinessConflictException("作品当前状态不允许打分：" + work.getStatus());
        }
        return work;
    }

    /**
     * 按当前生效配置即时重算单个未公示作品（行外入口：公示/配置流程之外的重算）。
     * 已公示作品直接短路——它的分数只能由公示批次快照决定。
     */
    @Transactional
    public void recalculateWorkGrade(Long workId) {
        Work work = workRepository.findById(workId)
                .orElseThrow(() -> new ResourceNotFoundException("Work not found with id: " + workId));
        if (Boolean.TRUE.equals(work.getPublished())) {
            return;
        }
        applyCurrentConfig(work);
    }

    /**
     * 提交/改分/删分事务内的重算：调用方已经持有作品行锁，直接用持有的实体即可。
     */
    private void recalculateWithinTransaction(Work work) {
        applyCurrentConfig(work);
    }

    /**
     * 按维度齐全性决定作品状态（总监口径：缺维不得进综合分）：
     * - 一条维度分都没有 → APPROVED，综合分/等级清空；
     * - 有部分维度分、缺至少一维 → PENDING_SCORES（待齐分），综合分/等级清空，绝不按零分凑；
     * - 四维齐全 → GRADED，按当前权重/合格线算综合分、等级、合格性。
     * 调用方需保证作品未公示（或处于撤回/配置刷新等内部流程）。
     */
    private void applyCurrentConfig(Work work) {
        List<Score> scores = scoreRepository.findByWorkId(work.getId());

        if (scores.isEmpty()) {
            work.setStatus("APPROVED");
            clearGrade(work);
            workRepository.save(work);
            return;
        }

        Set<String> present = scores.stream().map(Score::getDimension).collect(Collectors.toSet());
        if (!present.containsAll(Dimensions.ALL)) {
            work.setStatus("PENDING_SCORES");
            clearGrade(work);
            workRepository.save(work);
            return;
        }

        ScoringConfig config = configService.getCurrentScoringConfig();
        GradeResult result = gradingService.calculate(scores, config)
                .orElseThrow(() -> new IllegalStateException("四维标记齐全却算不出综合分，数据异常: workId=" + work.getId()));

        work.setTotalScore(result.totalScore());
        work.setGrade(result.grade());
        work.setIsQualified(result.qualified());
        work.setStatus("GRADED");
        workRepository.save(work);
    }

    private void clearGrade(Work work) {
        work.setTotalScore(null);
        work.setGrade(null);
        work.setIsQualified(false);
    }

    private void validateJudge(Long judgeId, String judgeName) {
        if (judgeId == null) {
            throw new BusinessValidationException("缺少评委ID");
        }
        if (judgeName == null || judgeName.trim().isEmpty()) {
            throw new BusinessValidationException("缺少评委姓名");
        }
    }

    /** 只接受四个已知维度，忽略请求里的未知 key，并保持固定维度顺序。 */
    private Map<String, BigDecimal> normalizeRequested(Map<String, BigDecimal> requested) {
        Map<String, BigDecimal> normalized = new LinkedHashMap<>();
        if (requested == null) {
            return normalized;
        }
        for (String dimension : Dimensions.ALL) {
            if (requested.containsKey(dimension)) {
                normalized.put(dimension, requested.get(dimension));
            }
        }
        return normalized;
    }

    private void validateDimensionValue(String dimension, BigDecimal value) {
        if (!Dimensions.isKnown(dimension)) {
            throw new BusinessValidationException("未知打分维度：" + dimension);
        }
        if (value == null) {
            throw new BusinessValidationException(dimensionLabel(dimension) + " 的分数不能为空（缺维度请直接不传该维度，而不是传 0 或空值）");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessValidationException(dimensionLabel(dimension) + " 的分数必须在 0-100 之间");
        }
    }

    static String dimensionLabel(String dimension) {
        return switch (dimension) {
            case Dimensions.CREATIVITY -> "创意";
            case Dimensions.COMPLETION -> "完成度";
            case Dimensions.COMMERCIAL_POTENTIAL -> "商业潜力";
            case Dimensions.CRAFTSMANSHIP -> "工艺";
            default -> dimension;
        };
    }

    /** 作品当前缺失的维度名（下划线口径），保持固定顺序。 */
    public static List<String> missingDimensions(List<Score> scores) {
        Set<String> present = scores.stream().map(Score::getDimension).collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        for (String dimension : Dimensions.ALL) {
            if (!present.contains(dimension)) {
                missing.add(dimension);
            }
        }
        return missing;
    }

    /** 作品当前缺失维度的中文名，用于待齐分提示。 */
    public static List<String> missingDimensionLabels(List<Score> scores) {
        return missingDimensions(scores).stream()
                .map(ScoreService::dimensionLabel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<Score> findScore(Long workId, String dimension) {
        return scoreRepository.findByWorkIdAndDimension(workId, dimension);
    }

}
