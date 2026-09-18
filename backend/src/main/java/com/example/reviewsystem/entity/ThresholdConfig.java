package com.example.reviewsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "threshold_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "qualified_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal qualifiedScore = new BigDecimal("60");

    @Column(name = "extreme_threshold", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal extremeThreshold = new BigDecimal("20");

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}