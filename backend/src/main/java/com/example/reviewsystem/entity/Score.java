package com.example.reviewsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评委维度分流水：一条记录 = 某评委对某作品某一个维度给出的一条有效分。
 * 同一 (work_id, dimension) 只允许存在一条有效分（DB 唯一约束兜底，
 * 正常路径由作品行锁串行化）；第二位评委抢同一维度时提交被拒绝（409），
 * 页面上能看到该维度已由谁打过。
 */
@Entity
@Table(name = "score",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_main_score_work_dimension",
                columnNames = {"work_id", "dimension"}),
        indexes = {
                @Index(name = "idx_main_score_work_id", columnList = "work_id"),
                @Index(name = "idx_main_score_judge_id", columnList = "judge_id"),
                @Index(name = "idx_main_score_dimension", columnList = "dimension"),
                @Index(name = "idx_main_score_scored_at", columnList = "scored_at")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Score {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_id", nullable = false)
    private Long workId;

    @Column(name = "judge_id", nullable = false)
    private Long judgeId;

    @Column(name = "judge_name", nullable = false, length = 100)
    private String judgeName;

    /** creativity / completion / commercial_potential / craftsmanship */
    @Column(nullable = false, length = 50)
    private String dimension;

    /** 该维度得分（0-100）。缺维度不是"零分"，而是根本没有这一行。 */
    @Column(name = "score_value", nullable = false, precision = 5, scale = 2)
    private BigDecimal value;

    @Column(name = "scored_at")
    @Builder.Default
    private LocalDateTime scoredAt = LocalDateTime.now();

}
