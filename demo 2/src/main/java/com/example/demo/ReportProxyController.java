package com.example.demo;

import com.example.demo.dto.FileStorageResult;
import com.example.demo.service.CsvReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
public class ReportProxyController {

    private static final Logger log = LoggerFactory.getLogger(ReportProxyController.class);
    private static final ParameterizedTypeReference<List<Map<String, Object>>> REPORT_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<String>> FILE_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final RestTemplate restTemplate;
    private final CsvReportService csvReportService;
    private final String reportServiceUrl;
    private final String fileStorageServiceUrl;

    public ReportProxyController(
            RestTemplate restTemplate,
            CsvReportService csvReportService,
            @Value("${report.service.url:http://localhost:8081}") String reportServiceUrl,
            @Value("${file.storage.service-url:http://localhost:8082}") String fileStorageServiceUrl) {
        this.restTemplate = restTemplate;
        this.csvReportService = csvReportService;
        this.reportServiceUrl = trimTrailingSlash(reportServiceUrl);
        this.fileStorageServiceUrl = trimTrailingSlash(fileStorageServiceUrl);
    }

    @GetMapping("/cars")
    public List<Map<String, Object>> getCarsReport() {
        return fetchCarsReport();
    }

    @PostMapping("/cars/export")
    public ResponseEntity<FileStorageResult> exportCarsReport() {
        List<Map<String, Object>> rows = fetchCarsReport();
        byte[] csvBytes = csvReportService.generate(rows);
        String filename = createFilename();

        log.info("CSV report generated filename={} rows={} sizeBytes={} instance={}",
                filename, rows.size(), csvBytes.length, instanceId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set("X-Filename", filename);

        HttpEntity<byte[]> request = new HttpEntity<>(csvBytes, headers);
        ResponseEntity<FileStorageResult> stored = restTemplate.exchange(
                fileStorageServiceUrl + "/api/files",
                HttpMethod.POST,
                request,
                FileStorageResult.class
        );

        log.info("CSV report uploaded to file-storage-service filename={} target={} status={}",
                filename, fileStorageServiceUrl, stored.getStatusCode().value());

        if (stored.getBody() == null) {
            throw new IllegalStateException("File storage service returned an empty response");
        }
        return ResponseEntity.status(stored.getStatusCode()).body(stored.getBody());
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
        log.info("HTTP GET /reports/cars forwarding to report-service target={} instance={}",
                reportServiceUrl, instanceId());

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                reportServiceUrl + "/reports/cars",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                REPORT_TYPE
        );

        List<Map<String, Object>> rows = response.getBody() != null
                ? response.getBody() : List.of();
        log.info("HTTP GET /reports/cars response received from target={} status={} rows={}",
                reportServiceUrl, response.getStatusCode().value(), rows.size());
        return rows;
    }

    private String createFilename() {
        return "cars-report-" + LocalDateTime.now().format(FILE_DATE_FORMAT)
                + "-" + UUID.randomUUID().toString().substring(0, 8) + ".csv";
    }

    private String instanceId() {
        String hostname = System.getenv("HOSTNAME");
        return hostname != null ? hostname : "local";
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
