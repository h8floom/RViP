package com.example.demo;

import com.example.demo.dto.ReportExportResponse;
import com.example.demo.dto.ReportExportStatusResponse;
import com.example.demo.service.ReportExportSagaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
public class ReportProxyController {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> REPORT_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<String>> FILE_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final ReportExportSagaService sagaService;
    private final String reportServiceUrl;
    private final String fileStorageServiceUrl;

    public ReportProxyController(
            RestTemplate restTemplate,
            ReportExportSagaService sagaService,
            @Value("${report.service.url:http://localhost:8081}") String reportServiceUrl,
            @Value("${file.storage.service-url:http://localhost:8082}") String fileStorageServiceUrl) {
        this.restTemplate = restTemplate;
        this.sagaService = sagaService;
        this.reportServiceUrl = trimTrailingSlash(reportServiceUrl);
        this.fileStorageServiceUrl = trimTrailingSlash(fileStorageServiceUrl);
    }

    @GetMapping("/cars")
    public List<Map<String, Object>> getCarsReport() {
        return fetchCarsReport();
    }

    @PostMapping("/cars/export")
    public ResponseEntity<ReportExportResponse> exportCarsReport() {
        return ResponseEntity.status(201).body(sagaService.exportCarsReport());
    }

    @GetMapping("/exports/{exportId}")
    public ReportExportStatusResponse getExportStatus(@PathVariable UUID exportId) {
        return sagaService.getStatus(exportId);
    }

    @PostMapping("/exports/{exportId}/compensate")
    public ReportExportStatusResponse retryCompensation(@PathVariable UUID exportId) {
        return sagaService.retryCompensation(exportId);
    }

    @GetMapping("/files")
    public List<String> listStoredFiles() {
        ResponseEntity<List<String>> response = restTemplate.exchange(
                fileStorageServiceUrl + "/api/files",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                FILE_LIST_TYPE
        );
        return response.getBody() != null ? response.getBody() : List.of();
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<byte[]> downloadStoredFile(@PathVariable String filename) {
        URI uri = UriComponentsBuilder.fromUriString(fileStorageServiceUrl)
                .pathSegment("api", "files", filename)
                .build()
                .encode()
                .toUri();

        ResponseEntity<byte[]> response = restTemplate.exchange(
                uri, HttpMethod.GET, HttpEntity.EMPTY, byte[].class);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
    }

    private List<Map<String, Object>> fetchCarsReport() {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                reportServiceUrl + "/reports/cars",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                REPORT_TYPE
        );
        return response.getBody() != null ? response.getBody() : List.of();
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
