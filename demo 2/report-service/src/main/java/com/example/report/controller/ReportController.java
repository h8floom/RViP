package com.example.report.controller;

import com.example.report.dto.CarReportDto;
import com.example.report.service.CarReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final CarReportService carReportService;

    public ReportController(CarReportService carReportService) {
        this.carReportService = carReportService;
    }

    @GetMapping("/cars")
    public List<CarReportDto> getCarsReport() {
        System.out.println(
            "REQUEST /reports/cars обработан контейнером: "
                    + System.getenv("HOSTNAME")
    );
        return carReportService.getAllCars();
    }
}