package com.example.reviewsystem.repository;

import com.example.reviewsystem.entity.Score;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScoreRepository extends JpaRepository<Score, Long> {

    List<Score> findByWorkId(Long workId);

    Optional<Score> findByWorkIdAndDimension(Long workId, String dimension);

    @Query("SELECT s FROM Score s WHERE s.workId = :workId AND s.dimension IN :dimensions")
    List<Score> findByWorkIdAndDimensionIn(@Param("workId") Long workId,
                                           @Param("dimensions") List<String> dimensions);

    /**
     * 某维度的全局平均分，只统计四维已齐全（status=GRADED）的作品。
     * 缺维作品停在 PENDING_SCORES，既不产生综合分，也不允许把任何半成品维度分垫进统计。
     */
    @Query("SELECT AVG(s.value) FROM Score s " +
            "WHERE s.dimension = :dimension " +
            "AND s.workId IN (SELECT w.id FROM Work w WHERE w.status = 'GRADED')")
    BigDecimal avgValueByDimensionForGradedWorks(@Param("dimension") String dimension);

}
