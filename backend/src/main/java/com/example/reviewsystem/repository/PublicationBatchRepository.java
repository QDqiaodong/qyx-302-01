package com.example.reviewsystem.repository;

import com.example.reviewsystem.entity.PublicationBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublicationBatchRepository extends JpaRepository<PublicationBatch, Long> {

    List<PublicationBatch> findAllByOrderByPublishedAtDesc();

    List<PublicationBatch> findByStatusOrderByPublishedAtDesc(String status);
}
