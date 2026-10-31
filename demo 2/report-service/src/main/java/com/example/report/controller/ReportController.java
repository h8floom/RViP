package com.example.report.controller;

import com.example.report.dto.CarReportDto;
import com.example.report.service.CarReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);
    private final CarReportService carReportService;

    public ReportController(CarReportService carReportService) {
        this.carReportService = carReportService;
    }

    @GetMapping("/cars")
    public List<CarReportDto> getCarsReport() {
        log.info("HTTP GET /reports/cars handled by instance={}", instanceId());
        List<CarReportDto> rows = carReportService.getAllCars();
        log.info("HTTP GET /reports/cars completed rows={} instance={}", rows.size(), instanceId());
        return rows;
    }

    private String instanceId() {
        String hostname = System.getenv("HOSTNAME");
        return hostname != null ? hostname : "local";
    }
}
