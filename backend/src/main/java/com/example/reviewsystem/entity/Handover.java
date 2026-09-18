package com.example.reviewsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 现场点交单：作者把实物送到展厅时开立，一张作品同时只有一张。
 * 成立要件（三样缺一不可）：件数（取自作品档案）、完好情况、接收人。
 * 状态机：未点交（无点交单）→ 点交中（IN_PROGRESS）→ 已点交（COMPLETED）；
 * 点交中途失败或已点交后件数被改，整张单作废删除，作品退回未点交。
 */
@Entity
@Table(name = "handover", indexes = {
        @Index(name = "idx_handover_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Handover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 一件作品同时只有一张点交单（唯一约束兜底）。 */
    @Column(name = "work_id", nullable = false, unique = true)
    private Long workId;

    /** IN_PROGRESS 点交中 / COMPLETED 已点交；未点交 = 没有点交单行。 */
    @Column(nullable = false, length = 20)
    private String status;

    /** 完好情况（三要素之一），如：完好 / 轻微磨损 / 破损待确认。 */
    @Column(name = "condition_status", length = 100)
    private String conditionStatus;

    /** 接收人（三要素之一）。 */
    @Column(name = "receiver", length = 100)
    private String receiver;

    /** 点交完成时记录的件数快照（三要素之一，取自作品档案件数）。 */
    @Column(name = "piece_count_snapshot")
    private Integer pieceCountSnapshot;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

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
