package com.example.reviewsystem.service;

import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.exception.BusinessValidationException;
import com.example.reviewsystem.repository.HandoverRepository;
import com.example.reviewsystem.repository.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkService {

    private final WorkRepository workRepository;
    private final HandoverRepository handoverRepository;
    private final HandoverService handoverService;

    @Transactional(readOnly = true)
    public Page<Work> getWorks(String category, String status, Boolean published, String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isEmpty()) {
            return workRepository.searchByKeyword(keyword, pageable);
        }
        boolean hasCategory = category != null && !category.isEmpty();
        boolean hasStatus = status != null && !status.isEmpty();
        boolean hasPublished = published != null;

        if (hasCategory && hasStatus && hasPublished) {
            return workRepository.findByCategoryAndStatusAndPublished(category, status, published, pageable);
        }
        if (hasCategory && hasStatus) {
            return workRepository.findByCategoryAndStatus(category, status, pageable);
        }
        if (hasCategory && hasPublished) {
            return workRepository.findByCategoryAndPublished(category, published, pageable);
        }
        if (hasStatus && hasPublished) {
            return workRepository.findByStatusAndPublished(status, published, pageable);
        }
        if (hasCategory) {
            return workRepository.findByCategory(category, pageable);
        }
        if (hasStatus) {
            return workRepository.findByStatus(status, pageable);
        }
        if (hasPublished) {
            return workRepository.findByPublished(published, pageable);
        }
        return workRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Work> getWorkById(Long id) {
        return workRepository.findById(id);
    }

    @Transactional
    public Work createWork(Work work) {
        validatePieceCount(work.getPieceCount());
        work.setStatus("PENDING");
        work.setIsQualified(false);
        return workRepository.save(work);
    }

    @Transactional
    public Work updateWork(Long id, Work workDetails) {
        // 行锁内读取：与并发的"完成点交"互斥，避免件数变更与点交完成交叉提交
        Work work = workRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Work not found with id: " + id));

        if (workDetails.getCategory() != null) {
            work.setCategory(workDetails.getCategory());
        }
        if (workDetails.getTheme() != null) {
            work.setTheme(workDetails.getTheme());
        }
        if (workDetails.getCreatorName() != null) {
            work.setCreatorName(workDetails.getCreatorName());
        }
        if (workDetails.getCreatorPhone() != null) {
            work.setCreatorPhone(workDetails.getCreatorPhone());
        }
        if (workDetails.getCreatorEmail() != null) {
            work.setCreatorEmail(workDetails.getCreatorEmail());
        }
        if (workDetails.getWorkName() != null) {
            work.setWorkName(workDetails.getWorkName());
        }
        if (workDetails.getDescription() != null) {
            work.setDescription(workDetails.getDescription());
        }
        if (workDetails.getImageUrl() != null) {
            work.setImageUrl(workDetails.getImageUrl());
        }
        if (workDetails.getStatus() != null) {
            work.setStatus(workDetails.getStatus());
        }
        if (workDetails.getPieceCount() != null) {
            validatePieceCount(workDetails.getPieceCount());
            if (!Objects.equals(work.getPieceCount(), workDetails.getPieceCount())) {
                // 件数被改掉：已点交作品必须先撤下展墙、撤销参展凭证、点交单作废，
                // 退回未点交；重新点交完成前不得再上墙。
                handoverService.invalidateForPieceCountChange(work);
                work.setPieceCount(workDetails.getPieceCount());
            }
        }

        return workRepository.save(work);
    }

    @Transactional
    public void deleteWork(Long id) {
        // 先清掉点交单，避免作品删了点交单成孤儿
        handoverRepository.deleteByWorkId(id);
        workRepository.deleteById(id);
    }

    @Transactional
    public Work approveWork(Long id) {
        Work work = workRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Work not found with id: " + id));
        work.setStatus("APPROVED");
        return workRepository.save(work);
    }

    @Transactional(readOnly = true)
    public List<Work> getApprovedWorks() {
        return workRepository.findApprovedWorks();
    }

    private void validatePieceCount(Integer pieceCount) {
        if (pieceCount != null && pieceCount <= 0) {
            throw new BusinessValidationException("件数必须大于 0");
        }
    }

}