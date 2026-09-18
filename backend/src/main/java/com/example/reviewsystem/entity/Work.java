package com.example.reviewsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "work", indexes = {
        @Index(name = "idx_category", columnList = "category"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_grade", columnList = "grade"),
        @Index(name = "idx_published", columnList = "published"),
        @Index(name = "idx_publication_batch", columnList = "publication_batch_id"),
        @Index(name = "idx_work_handover_status", columnList = "handover_status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Work {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(length = 200)
    private String theme;

    @Column(name = "creator_name", nullable = false, length = 100)
    private String creatorName;

    @Column(name = "creator_phone", length = 20)
    private String creatorPhone;

    @Column(name = "creator_email", length = 100)
    private String creatorEmail;

    @Column(name = "work_name", nullable = false, length = 200)
    private String workName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * PENDING 待审核 / APPROVED 审核通过、尚无打分 / PENDING_SCORES 待齐分（已有部分维度分，
     * 缺维不得算综合分）/ GRADED 四维齐全、综合分已算出。
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "total_score", precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Column(length = 10)
    private String grade;

    @Column(name = "is_qualified")
    @Builder.Default
    private Boolean isQualified = false;

    /**
     * 是否已对外公示。true 时综合分/等级/合格性以公示快照为准，
     * 打分提交与配置变更都不得再重算该作品。
     */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean published = false;

    /**
     * 所属公示批次。公示时作品被整批锁入同一条快照批次；撤回时置空。
     */
    @Column(name = "publication_batch_id")
    private Long publicationBatchId;

    // ---- 公示当时钉死的结果快照（published=false 时与即时列保持一致/为空）----

    @Column(name = "published_total_score", precision = 5, scale = 2)
    private BigDecimal publishedTotalScore;

    @Column(name = "published_grade", length = 10)
    private String publishedGrade;

    @Column(name = "published_is_qualified")
    private Boolean publishedIsQualified;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // ---- 现场点交（实物到厅）----

    /**
     * 件数（作者申报，点交时核对的口径）。已点交之后该值一旦被改掉，
     * 点交单立即作废：展墙撤下、参展凭证撤销，重新点交完成前不得再上墙。
     */
    @Column(name = "piece_count")
    private Integer pieceCount;

    /**
     * 点交状态：NOT_STARTED 未点交 / IN_PROGRESS 点交中 / COMPLETED 已点交。
     * 只能 未点交 → 点交中 → 已点交 逐步推进；点交失败或件数被改一律退回未点交。
     * 展陈看板只取 COMPLETED，参展凭证只对 COMPLETED 发放。
     */
    @Column(name = "handover_status", nullable = false,
            columnDefinition = "varchar(20) not null default 'NOT_STARTED'")
    @Builder.Default
    private String handoverStatus = "NOT_STARTED";

    /**
     * 参展凭证：NOT_ISSUED 未发放 / ISSUED 已发放 / REVOKED 已撤销（件数变更撤下后留痕）。
     */
    @Column(name = "certificate_status", nullable = false,
            columnDefinition = "varchar(20) not null default 'NOT_ISSUED'")
    @Builder.Default
    private String certificateStatus = "NOT_ISSUED";

    @Column(name = "certificate_no", length = 64)
    private String certificateNo;

    @Column(name = "certificate_issued_at")
    private LocalDateTime certificateIssuedAt;

    @Column(name = "certificate_revoked_at")
    private LocalDateTime certificateRevokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}