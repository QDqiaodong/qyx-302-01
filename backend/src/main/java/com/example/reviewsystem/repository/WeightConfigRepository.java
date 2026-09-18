package com.example.reviewsystem.repository;

import com.example.reviewsystem.entity.WeightConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WeightConfigRepository extends JpaRepository<WeightConfig, Long> {

    Optional<WeightConfig> findByDimension(String dimension);

    /**
     * 行级写锁读取：公示拍快照 / 保存新权重时使用，
     * 防止"公示进行中另一个请求改权重"造成批次内口径不一致。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WeightConfig w")
    List<WeightConfig> findAllForUpdate();
}
