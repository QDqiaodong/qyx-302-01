package com.example.reviewsystem.service;

import com.example.reviewsystem.entity.ThresholdConfig;
import com.example.reviewsystem.entity.WeightConfig;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.exception.BusinessValidationException;
import com.example.reviewsystem.repository.ScoreRepository;
import com.example.reviewsystem.repository.ThresholdConfigRepository;
import com.example.reviewsystem.repository.WeightConfigRepository;
import com.example.reviewsystem.repository.WorkRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ConfigService {

    private final WeightConfigRepository weightConfigRepository;
    private final ThresholdConfigRepository thresholdConfigRepository;
    private final WorkRepository workRepository;
    private final ScoreRepository scoreRepository;
    private final GradingService gradingService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String WEIGHTS_KEY = "score:weights:sorted";
    private static final long CACHE_TTL = 300;

    @PostConstruct
    public void init() {
        initDefaultWeights();
        initDefaultThreshold();
        refreshWeightCache();
    }

    private void initDefaultWeights() {
        if (weightConfigRepository.count() == 0) {
            weightConfigRepository.save(WeightConfig.builder().dimension("creativity").weight(new BigDecimal("0.30")).build());
            weightConfigRepository.save(WeightConfig.builder().dimension("completion").weight(new BigDecimal("0.25")).build());
            weightConfigRepository.save(WeightConfig.builder().dimension("commercial_potential").weight(new BigDecimal("0.25")).build());
            weightConfigRepository.save(WeightConfig.builder().dimension("craftsmanship").weight(new BigDecimal("0.20")).build());
        }
    }

    private void initDefaultThreshold() {
        if (thresholdConfigRepository.count() == 0) {
            thresholdConfigRepository.save(ThresholdConfig.builder()
                    .qualifiedScore(new BigDecimal("60"))
                    .extremeThreshold(new BigDecimal("20"))
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getWeights() {
        Set<ZSetOperations.TypedTuple<String>> cachedTuples = null;
        try {
            ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();
            cachedTuples = zSetOps.rangeWithScores(WEIGHTS_KEY, 0, -1);
        } catch (Exception e) {
            // Redis 不可用时直接回退数据库，不阻断取配置
            logCacheFailure(e);
        }

        if (cachedTuples != null && !cachedTuples.isEmpty()) {
            Map<String, BigDecimal> weights = new HashMap<>();
            for (ZSetOperations.TypedTuple<String> tuple : cachedTuples) {
                String dimension = tuple.getValue();
                Double score = tuple.getScore();
                if (dimension != null && score != null) {
                    weights.put(dimension, BigDecimal.valueOf(score).setScale(2, BigDecimal.ROUND_HALF_UP));
                }
            }
            if (!weights.isEmpty()) {
                return weights;
            }
        }

        Map<String, BigDecimal> weights = readWeights();
        saveWeightCache(weights);
        return weights;
    }

    private Map<String, BigDecimal> readWeights() {
        List<WeightConfig> configs = weightConfigRepository.findAll();
        Map<String, BigDecimal> weights = new HashMap<>();
        for (WeightConfig config : configs) {
            weights.put(config.getDimension(), config.getWeight());
        }
        return weights;
    }

    /**
     * 当前生效口径，直接读库（不经 Redis）。公示拍快照必须用它，避免拿到 5 分钟旧缓存。
     */
    @Transactional(readOnly = true)
    public ScoringConfig getCurrentScoringConfig() {
        Map<String, BigDecimal> weights = readWeights();
        ThresholdConfig threshold = getThresholdEntity();
        return gradingService.buildConfig(weights,
                threshold.getQualifiedScore(), threshold.getExtremeThreshold());
    }

    /**
     * 行锁内读取当前口径：与保存配置、公示批次互斥，保证整批快照取自同一刻配置。
     */
    @Transactional(readOnly = true)
    public ScoringConfig getCurrentScoringConfigForUpdate() {
        Map<String, BigDecimal> weights = new HashMap<>();
        for (WeightConfig config : weightConfigRepository.findAllForUpdate()) {
            weights.put(config.getDimension(), config.getWeight());
        }
        ThresholdConfig threshold = thresholdConfigRepository.findAllForUpdate().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessValidationException("阈值配置不存在"));
        return gradingService.buildConfig(weights,
                threshold.getQualifiedScore(), threshold.getExtremeThreshold());
    }

    /**
     * 保存新权重后，只对未公示的已评分作品按新口径即时重算；
     * 已公示作品的快照一行都不碰，继续展示公示当时的结果。
     */
    @Transactional
    public Map<String, BigDecimal> updateWeights(Map<String, BigDecimal> weights) {
        validateWeights(weights);

        // 先拿全部配置行写锁（与并发公示互斥），再改权重。
        weightConfigRepository.findAllForUpdate();
        thresholdConfigRepository.findAllForUpdate();
        for (Map.Entry<String, BigDecimal> entry : weights.entrySet()) {
            WeightConfig config = weightConfigRepository.findByDimension(entry.getKey())
                    .orElse(WeightConfig.builder().dimension(entry.getKey()).build());
            config.setWeight(entry.getValue());
            weightConfigRepository.save(config);
        }

        ScoringConfig newConfig = gradingService.buildConfig(weights,
                getThresholdEntity().getQualifiedScore(),
                getThresholdEntity().getExtremeThreshold());
        recalculateUnpublished(newConfig);
        refreshWeightCache();
        return weights;
    }

    private void validateWeights(Map<String, BigDecimal> weights) {
        String[] dimensions = {"creativity", "completion", "commercial_potential", "craftsmanship"};
        BigDecimal sum = BigDecimal.ZERO;
        for (String dimension : dimensions) {
            BigDecimal weight = weights.get(dimension);
            if (weight == null) {
                throw new BusinessValidationException("缺少维度权重：" + dimension);
            }
            sum = sum.add(weight);
        }
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BusinessValidationException("四维权重之和必须为 1，当前为 " + sum);
        }
    }

    /**
     * 保存合格线/极端分阈值后，同样只刷新未公示已评分作品。
     */
    @Transactional
    public ThresholdConfig updateThreshold(ThresholdConfig threshold) {
        if (threshold.getQualifiedScore() == null || threshold.getExtremeThreshold() == null) {
            throw new BusinessValidationException("合格线与极端分阈值不能为空");
        }

        // 先拿全部配置行写锁（与并发公示互斥），再改阈值。
        weightConfigRepository.findAllForUpdate();
        thresholdConfigRepository.findAllForUpdate();
        ThresholdConfig existing = thresholdConfigRepository.findAll().stream()
                .findFirst()
                .orElse(new ThresholdConfig());

        existing.setQualifiedScore(threshold.getQualifiedScore());
        existing.setExtremeThreshold(threshold.getExtremeThreshold());
        ThresholdConfig saved = thresholdConfigRepository.save(existing);

        recalculateUnpublished(getCurrentScoringConfigForUpdate());
        return saved;
    }

    private void recalculateUnpublished(ScoringConfig config) {
        List<Work> unpublished = workRepository.findGradedUnpublishedForUpdate();
        for (Work work : unpublished) {
            var scores = scoreRepository.findByWorkId(work.getId());
            // findGradedUnpublishedForUpdate 只选 GRADED（四维齐全）作品；
            // 若数据异常导致缺维，绝不用零分凑——直接跳过，综合分不写入。
            gradingService.calculate(scores, config).ifPresent(result -> {
                work.setTotalScore(result.totalScore());
                work.setGrade(result.grade());
                work.setIsQualified(result.qualified());
                workRepository.save(work);
            });
        }
    }

    private void saveWeightCache(Map<String, BigDecimal> weights) {
        try {
            ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();
            stringRedisTemplate.delete(WEIGHTS_KEY);
            for (Map.Entry<String, BigDecimal> entry : weights.entrySet()) {
                zSetOps.add(WEIGHTS_KEY, entry.getKey(), entry.getValue().doubleValue());
            }
            stringRedisTemplate.expire(WEIGHTS_KEY, CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Redis 仅作缓存：不可用时回退数据库直读，不影响公示/打分主流程。
            logCacheFailure(e);
        }
    }

    private void logCacheFailure(Exception e) {
        org.slf4j.LoggerFactory.getLogger(ConfigService.class)
                .warn("写入权重缓存失败，已回退数据库直读: {}", e.getMessage());
    }

    public void refreshWeightCache() {
        saveWeightCache(readWeights());
    }

    @Transactional(readOnly = true)
    public ThresholdConfig getThreshold() {
        return getThresholdEntity();
    }

    private ThresholdConfig getThresholdEntity() {
        return thresholdConfigRepository.findAll().stream()
                .findFirst()
                .orElse(ThresholdConfig.builder()
                        .qualifiedScore(new BigDecimal("60"))
                        .extremeThreshold(new BigDecimal("20"))
                        .build());
    }
}
