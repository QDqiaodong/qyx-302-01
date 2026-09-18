package com.example.reviewsystem.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 评委一次提交的维度分。允许只交一个或几个维度（打分面板支持只交创意分），
 * 但至少要带一个维度；请求体维度名用下划线口径：
 * dimensions: {"creativity": 90, "completion": 80}
 */
@Data
public class ScoreRequest {

    private Long workId;
    private Long judgeId;
    private String judgeName;

    /** 维度名（creativity/completion/commercial_potential/craftsmanship）→ 分数。 */
    private Map<String, BigDecimal> dimensions;

}
