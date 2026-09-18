package com.example.reviewsystem.controller;

import com.example.reviewsystem.dto.request.ThresholdConfigRequest;
import com.example.reviewsystem.dto.request.WeightConfigRequest;
import com.example.reviewsystem.entity.ThresholdConfig;
import com.example.reviewsystem.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping("/weights")
    public ResponseEntity<Map<String, BigDecimal>> getWeights() {
        Map<String, BigDecimal> weights = configService.getWeights();
        return ResponseEntity.ok(weights);
    }

    @PutMapping("/weights")
    public ResponseEntity<Map<String, BigDecimal>> updateWeights(@RequestBody WeightConfigRequest request) {
        Map<String, BigDecimal> weights = new HashMap<>();
        weights.put("creativity", request.getCreativity());
        weights.put("completion", request.getCompletion());
        weights.put("commercial_potential", request.getCommercialPotential());
        weights.put("craftsmanship", request.getCraftsmanship());

        Map<String, BigDecimal> updated = configService.updateWeights(weights);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/threshold")
    public ResponseEntity<ThresholdConfig> getThreshold() {
        ThresholdConfig threshold = configService.getThreshold();
        return ResponseEntity.ok(threshold);
    }

    @PutMapping("/threshold")
    public ResponseEntity<ThresholdConfig> updateThreshold(@RequestBody ThresholdConfigRequest request) {
        ThresholdConfig threshold = ThresholdConfig.builder()
                .qualifiedScore(request.getQualifiedScore())
                .extremeThreshold(request.getExtremeThreshold())
                .build();

        ThresholdConfig updated = configService.updateThreshold(threshold);
        return ResponseEntity.ok(updated);
    }

}