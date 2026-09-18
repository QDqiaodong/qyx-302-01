package com.example.reviewsystem.service;

import java.math.BigDecimal;

/**
 * 按某套口径算出的结果：综合分、等级、是否合格。
 * 公示时该结果会被钉进 work 行与公示批次快照里，之后不再随配置变化。
 */
public record GradeResult(
        BigDecimal totalScore,
        String grade,
        boolean qualified
) {
}
