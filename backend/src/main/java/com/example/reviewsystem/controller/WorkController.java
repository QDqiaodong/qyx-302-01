package com.example.reviewsystem.controller;

import com.example.reviewsystem.dto.request.WorkRequest;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.service.WorkService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/works")
@RequiredArgsConstructor
public class WorkController {

    private final WorkService workService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getWorks(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Work> worksPage = workService.getWorks(category, status, published, keyword, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", worksPage.getContent());
        response.put("totalElements", worksPage.getTotalElements());
        response.put("totalPages", worksPage.getTotalPages());
        response.put("currentPage", worksPage.getNumber());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Work> getWorkById(@PathVariable Long id) {
        return workService.getWorkById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Work> createWork(@RequestBody WorkRequest request) {
        Work work = Work.builder()
                .category(request.getCategory())
                .theme(request.getTheme())
                .creatorName(request.getCreatorName())
                .creatorPhone(request.getCreatorPhone())
                .creatorEmail(request.getCreatorEmail())
                .workName(request.getWorkName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .pieceCount(request.getPieceCount())
                .build();

        Work created = workService.createWork(work);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Work> updateWork(@PathVariable Long id, @RequestBody WorkRequest request) {
        Work work = Work.builder()
                .category(request.getCategory())
                .theme(request.getTheme())
                .creatorName(request.getCreatorName())
                .creatorPhone(request.getCreatorPhone())
                .creatorEmail(request.getCreatorEmail())
                .workName(request.getWorkName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .status(request.getStatus())
                .pieceCount(request.getPieceCount())
                .build();

        Work updated = workService.updateWork(id, work);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWork(@PathVariable Long id) {
        workService.deleteWork(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Work> approveWork(@PathVariable Long id) {
        Work approved = workService.approveWork(id);
        return ResponseEntity.ok(approved);
    }

}