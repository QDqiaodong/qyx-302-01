package com.example.reviewsystem.service;

import java.math.BigDecimal;

/**
 * 一次评分计算所使用的口径（四维权重 + 合格线 + 极端分阈值）。
 * 公示批次会把当时的口径原样落库；未公示作品则取当前配置。
 */
public record ScoringConfig(
        BigDecimal creativityWeight,
        BigDecimal completionWeight,
        BigDecimal commercialPotentialWeight,
        BigDecimal craftsmanshipWeight,
        BigDecimal qualifiedScore,
        BigDecimal extremeThreshold
) {
    public BigDecimal weight(String dimension) {
        return switch (dimension) {
            case "creativity" -> creativityWeight;
            case "completion" -> completionWeight;
            case "commercial_potential" -> commercialPotentialWeight;
            case "craftsmanship" -> craftsmanshipWeight;
            default -> throw new IllegalArgumentException("Unknown dimension: " + dimension);
        };
    }
}
