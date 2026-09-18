package com.example.reviewsystem.repository;

import com.example.reviewsystem.entity.Handover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HandoverRepository extends JpaRepository<Handover, Long> {

    Optional<Handover> findByWorkId(Long workId);

    List<Handover> findByWorkIdIn(List<Long> workIds);

    void deleteByWorkId(Long workId);
}
