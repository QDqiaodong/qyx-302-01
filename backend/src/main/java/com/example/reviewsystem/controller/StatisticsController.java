package com.example.reviewsystem.controller;

import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/grade-distribution")
    public ResponseEntity<Map<String, Object>> getGradeDistribution() {
        Map<String, Object> distribution = statisticsService.getGradeDistribution();
        return ResponseEntity.ok(distribution);
    }

    @GetMapping("/dimension-distribution")
    public ResponseEntity<Map<String, Object>> getDimensionDistribution() {
        Map<String, Object> distribution = statisticsService.getDimensionDistribution();
        return ResponseEntity.ok(distribution);
    }

    @GetMapping("/failed-works")
    public ResponseEntity<List<Work>> getFailedWorks() {
        List<Work> failedWorks = statisticsService.getFailedWorks();
        return ResponseEntity.ok(failedWorks);
    }

    @GetMapping("/work/{workId}")
    public ResponseEntity<Map<String, Object>> getWorkStatistics(@PathVariable Long workId) {
        Map<String, Object> stats = statisticsService.getWorkStatistics(workId);
        return ResponseEntity.ok(stats);
    }

}