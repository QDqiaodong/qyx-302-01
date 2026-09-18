package com.example.reviewsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对外公示批次。一批作品在同一个事务里、按同一套权重/合格线快照锁定。
 * 批次一旦以 PUBLISHED 提交，其中作品的综合分/等级/合格性不再受配置变更影响。
 */
@Entity
@Table(name = "publication_batch", indexes = {
        @Index(name = "idx_pb_status", columnList = "status"),
        @Index(name = "idx_pb_published_at", columnList = "published_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_name", length = 200)
    private String batchName;

    /**
     * PUBLISHED：已对外公示，快照不可变；REVOKED：整批撤回（作品回到未公示）。
     * 不存在"部分公示"状态——单事务提交保证要么整批可见，要么整批不存在。
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PUBLISHED";

    @Column(name = "creativity_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal creativityWeight;

    @Column(name = "completion_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionWeight;

    @Column(name = "commercial_potential_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal commercialPotentialWeight;

    @Column(name = "craftsmanship_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal craftsmanshipWeight;

    @Column(name = "qualified_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal qualifiedScore;

    @Column(name = "extreme_threshold", nullable = false, precision = 5, scale = 2)
    private BigDecimal extremeThreshold;

    @Column(name = "work_count", nullable = false)
    @Builder.Default
    private Integer workCount = 0;

    @Column(name = "published_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime publishedAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
