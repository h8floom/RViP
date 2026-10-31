package com.example.demo.controller;

import com.example.demo.dto.CarReportResponse;
import com.example.demo.dto.CarRequest;
import com.example.demo.dto.CarResponse;
import com.example.demo.service.CarService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cars")
public class CarController {

    private static final Logger log = LoggerFactory.getLogger(CarController.class);
    private final CarService carService;

    public CarController(CarService carService) {
        this.carService = carService;
    }

    @PostMapping
    public ResponseEntity<CarResponse> create(@Valid @RequestBody CarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(carService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<CarResponse>> findAll() {
        log.info("HTTP GET /api/cars handled instance={}", instanceId());
        return ResponseEntity.ok(carService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CarResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(carService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CarResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CarRequest request) {
        return ResponseEntity.ok(carService.update(id, request));
    }

    @PostMapping("/{id}/sell")
    public ResponseEntity<CarResponse> sell(@PathVariable Long id) {
        return ResponseEntity.ok(carService.sell(id));
    }

    @GetMapping("/report")
    public ResponseEntity<CarReportResponse> getReport() {
        return ResponseEntity.ok(carService.getReport());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        carService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private String instanceId() {
        String hostname = System.getenv("HOSTNAME");
        return hostname != null ? hostname : "local";
    }
}
