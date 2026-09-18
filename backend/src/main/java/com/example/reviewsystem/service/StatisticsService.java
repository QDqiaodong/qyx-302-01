package com.example.reviewsystem.service;

import com.example.reviewsystem.entity.Dimensions;
import com.example.reviewsystem.entity.Score;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.repository.ScoreRepository;
import com.example.reviewsystem.repository.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final WorkRepository workRepository;
    private final ScoreRepository scoreRepository;

    /**
     * 等级分布：countByGrade 只统计 status=GRADED 的作品。
     * 缺维作品停在 PENDING_SCORES 且 grade 为空，天然不会出现在饼图与占比里——
     * 绝不允许把缺维当零分垫成一个 C 级再计入分布。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getGradeDistribution() {
        List<Object[]> results = workRepository.countByGrade();
        Map<String, Long> gradeCounts = new LinkedHashMap<>();
        gradeCounts.put("S", 0L);
        gradeCounts.put("A", 0L);
        gradeCounts.put("B", 0L);
        gradeCounts.put("C", 0L);

        long total = 0;
        for (Object[] result : results) {
            String grade = (String) result[0];
            Long count = (Long) result[1];
            if (grade != null && gradeCounts.containsKey(grade)) {
                gradeCounts.put(grade, count);
                total += count;
            }
        }

        Map<String, Double> gradePercentages = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : gradeCounts.entrySet()) {
            double percentage = total > 0 ? (entry.getValue() * 100.0 / total) : 0;
            gradePercentages.put(entry.getKey(), Math.round(percentage * 100) / 100.0);
        }

        Map<String, Object> distribution = new HashMap<>();
        distribution.put("gradeCounts", gradeCounts);
        distribution.put("gradePercentages", gradePercentages);
        distribution.put("total", total);

        return distribution;
    }

    /**
     * 各维度平均分：只取四维已齐全作品（status=GRADED）的维度分参与平均。
     * 待齐分（PENDING_SCORES）作品的任何已填维度都不计入，避免半成品把平均值拉偏。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDimensionDistribution() {
        Map<String, BigDecimal> dimensionAverages = new LinkedHashMap<>();
        dimensionAverages.put(Dimensions.CREATIVITY,
                gradedDimensionAverage(Dimensions.CREATIVITY));
        dimensionAverages.put(Dimensions.COMPLETION,
                gradedDimensionAverage(Dimensions.COMPLETION));
        dimensionAverages.put(Dimensions.COMMERCIAL_POTENTIAL,
                gradedDimensionAverage(Dimensions.COMMERCIAL_POTENTIAL));
        dimensionAverages.put(Dimensions.CRAFTSMANSHIP,
                gradedDimensionAverage(Dimensions.CRAFTSMANSHIP));

        Map<String, Object> distribution = new HashMap<>();
        distribution.put("dimensionAverages", dimensionAverages);
        distribution.put("dimensionNames", Map.of(
                Dimensions.CREATIVITY, "创意",
                Dimensions.COMPLETION, "完成度",
                Dimensions.COMMERCIAL_POTENTIAL, "商业潜力",
                Dimensions.CRAFTSMANSHIP, "工艺"
        ));

        return distribution;
    }

    private BigDecimal gradedDimensionAverage(String dimension) {
        BigDecimal avg = scoreRepository.avgValueByDimensionForGradedWorks(dimension);
        return avg != null ? avg : BigDecimal.ZERO;
    }

    /**
     * 落选作品：findFailedWorks 已限定 status=GRADED 且不合格。
     * 缺维作品没有综合分，不会也不可能混进落选名单。
     */
    @Transactional(readOnly = true)
    public List<Work> getFailedWorks() {
        return workRepository.findFailedWorks();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWorkStatistics(Long workId) {
        Work work = workRepository.findById(workId)
                .orElseThrow(() -> new RuntimeException("Work not found with id: " + workId));

        List<Score> scores = scoreRepository.findByWorkId(workId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("work", work);
        stats.put("scoreCount", scores.size());
        stats.put("missingDimensions", ScoreService.missingDimensionLabels(scores));

        if (!scores.isEmpty()) {
            Map<String, List<BigDecimal>> byDimension = scores.stream()
                    .collect(Collectors.groupingBy(Score::getDimension,
                            Collectors.mapping(Score::getValue, Collectors.toList())));
            stats.put("dimensionScores", byDimension);
        }

        return stats;
    }

}
