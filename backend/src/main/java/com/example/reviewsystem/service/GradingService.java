package com.example.reviewsystem.service;

import com.example.reviewsystem.entity.Dimensions;
import com.example.reviewsystem.entity.Score;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 纯计算服务：给定一件作品全部已有的维度分和一套口径，算出综合分/等级/是否合格。
 * 即时评分（当前口径）与公示锁分（快照口径）都必须走这里，保证公式只有一份。
 *
 * 齐分规则（评审总监口径）：一件作品四维各有且仅有一条有效分时才算齐分；
 * 缺任何一维都不得进入综合分计算——绝不允许把缺的维度当 0 分垫出综合分。
 * 每个维度只有一条有效分，故不再需要按评委平均/剔除极端分。
 */
@Service
public class GradingService {

    /**
     * 缺维时返回 Optional.empty()：调用方必须让作品停在 PENDING_SCORES，
     * 清空综合分/等级，且不得把作品送进统计平均或公示。
     */
    public Optional<GradeResult> calculate(List<Score> scores, ScoringConfig config) {
        Map<String, BigDecimal> byDimension = scores.stream()
                .collect(Collectors.toMap(Score::getDimension, Score::getValue, (first, second) -> first));

        BigDecimal creativity = byDimension.get(Dimensions.CREATIVITY);
        BigDecimal completion = byDimension.get(Dimensions.COMPLETION);
        BigDecimal commercialPotential = byDimension.get(Dimensions.COMMERCIAL_POTENTIAL);
        BigDecimal craftsmanship = byDimension.get(Dimensions.CRAFTSMANSHIP);

        // 缺任何一维：停在待齐分，不用零分凑综合分。
        if (creativity == null || completion == null
                || commercialPotential == null || craftsmanship == null) {
            return Optional.empty();
        }

        BigDecimal totalScore = creativity.multiply(config.creativityWeight())
                .add(completion.multiply(config.completionWeight()))
                .add(commercialPotential.multiply(config.commercialPotentialWeight()))
                .add(craftsmanship.multiply(config.craftsmanshipWeight()))
                .setScale(2, RoundingMode.HALF_UP);

        String grade = determineGrade(totalScore);
        boolean qualified = totalScore.compareTo(config.qualifiedScore()) >= 0;
        return Optional.of(new GradeResult(totalScore, grade, qualified));
    }

    public ScoringConfig buildConfig(Map<String, BigDecimal> weights,
                                     BigDecimal qualifiedScore,
                                     BigDecimal extremeThreshold) {
        return new ScoringConfig(
                weights.get(Dimensions.CREATIVITY),
                weights.get(Dimensions.COMPLETION),
                weights.get(Dimensions.COMMERCIAL_POTENTIAL),
                weights.get(Dimensions.CRAFTSMANSHIP),
                qualifiedScore,
                extremeThreshold
        );
    }

    private String determineGrade(BigDecimal score) {
        int scoreInt = score.intValue();
        if (scoreInt >= 90) {
            return "S";
        } else if (scoreInt >= 80) {
            return "A";
        } else if (scoreInt >= 60) {
            return "B";
        } else {
            return "C";
        }
    }
}
