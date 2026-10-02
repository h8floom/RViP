package com.example.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CarReportDto(
        Long id,
        String brand,
        String model,
        Integer year,
        String vin,
        BigDecimal price,
        String status,
        LocalDateTime createdAt,
        LocalDateTime soldAt
) {
}