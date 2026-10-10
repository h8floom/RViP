
package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/reports")
public class ReportProxyController {

    private final RestTemplate restTemplate;
    private final List<String> reportServiceUrls;

    private final AtomicInteger counter = new AtomicInteger(0);

    public ReportProxyController(
            RestTemplate restTemplate,
            @Value("${report.service.urls:http://localhost:8081}")
            String urls) {

        this.restTemplate = restTemplate;

        this.reportServiceUrls = Arrays.stream(urls.split(","))
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .toList();
    }

    @GetMapping("/cars")
    public List<Map<String, Object>> getCarsReport() {

        int index = Math.floorMod(
                counter.getAndIncrement(),
                reportServiceUrls.size()
        );

        String reportUrl = reportServiceUrls.get(index);

        System.out.println(
                "Запрос отчёта через RestTemplate отправлен в: "
                        + reportUrl
        );

        ResponseEntity<List<Map<String, Object>>> response =
                restTemplate.exchange(
                        reportUrl + "/reports/cars",
                        HttpMethod.GET,
                        HttpEntity.EMPTY,
                        new ParameterizedTypeReference<
                                List<Map<String, Object>>>() {}
                );

        return response.getBody() != null
                ? response.getBody()
                : List.of();
    }
}