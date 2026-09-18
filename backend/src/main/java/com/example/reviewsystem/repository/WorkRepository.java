package com.example.reviewsystem.repository;

import com.example.reviewsystem.entity.Work;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkRepository extends JpaRepository<Work, Long> {

    Page<Work> findByCategory(String category, Pageable pageable);

    Page<Work> findByStatus(String status, Pageable pageable);

    Page<Work> findByCategoryAndStatus(String category, String status, Pageable pageable);

    Page<Work> findByPublished(Boolean published, Pageable pageable);

    Page<Work> findByCategoryAndPublished(String category, Boolean published, Pageable pageable);

    Page<Work> findByStatusAndPublished(String status, Boolean published, Pageable pageable);

    Page<Work> findByCategoryAndStatusAndPublished(String category, String status, Boolean published, Pageable pageable);

    @Query("SELECT w FROM Work w WHERE w.workName LIKE %:keyword% OR w.creatorName LIKE %:keyword%")
    Page<Work> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT w FROM Work w WHERE w.isQualified = false AND w.status = 'GRADED'")
    List<Work> findFailedWorks();

    @Query("SELECT w.grade, COUNT(w) FROM Work w WHERE w.status = 'GRADED' GROUP BY w.grade")
    List<Object[]> countByGrade();

    @Query("SELECT w FROM Work w WHERE w.status = 'APPROVED'")
    List<Work> findApprovedWorks();

    /**
     * 未公示但已算出综合分的作品——保存新权重/合格线后只需对这些作品即时重算。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Work w WHERE w.status = 'GRADED' AND w.published = false")
    List<Work> findGradedUnpublishedForUpdate();

    /**
     * 公示批次内的作品行加写锁后再锁定，保证整批原子、且与并发改分互斥。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Work w WHERE w.id IN :ids")
    List<Work> findByIdsForUpdate(@Param("ids") List<Long> ids);

    /**
     * 按主键加行级写锁读取：评委提交/改分时使用，与公示事务互斥，杜绝"读到未公示、提交时已公示"的竞态。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Work w WHERE w.id = :id")
    Optional<Work> findByIdForUpdate(@Param("id") Long id);

    /**
     * 撤回批次时锁定该批全部作品行。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Work w WHERE w.publicationBatchId = :batchId")
    List<Work> findByPublicationBatchIdForUpdate(@Param("batchId") Long batchId);

    @Query("SELECT w FROM Work w WHERE w.publicationBatchId = :batchId")
    List<Work> findByPublicationBatchId(@Param("batchId") Long batchId);

    @Query("SELECT COUNT(w) FROM Work w WHERE w.publicationBatchId = :batchId")
    long countByPublicationBatchId(@Param("batchId") Long batchId);

    /**
     * 展陈看板数据源：只取指定点交状态的作品。看板只查 COMPLETED，
     * 未点交/点交中的作品从查询层面就不会出现。
     */
    List<Work> findByHandoverStatus(String handoverStatus);
}
