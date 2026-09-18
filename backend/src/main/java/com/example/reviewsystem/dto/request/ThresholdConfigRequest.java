package com.example.reviewsystem.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ThresholdConfigRequest {

    private BigDecimal qualifiedScore;
    private BigDecimal extremeThreshold;

}