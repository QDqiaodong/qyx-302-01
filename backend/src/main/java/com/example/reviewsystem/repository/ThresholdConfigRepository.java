package com.example.reviewsystem.repository;

import com.example.reviewsystem.entity.ThresholdConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThresholdConfigRepository extends JpaRepository<ThresholdConfig, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ThresholdConfig t")
    List<ThresholdConfig> findAllForUpdate();
}
