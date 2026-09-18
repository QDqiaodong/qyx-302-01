package com.example.reviewsystem.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WeightConfigRequest {

    private BigDecimal creativity;
    private BigDecimal completion;
    private BigDecimal commercialPotential;
    private BigDecimal craftsmanship;

}