package com.example.demo.service;

import com.example.demo.dto.FileStorageResult;
import com.example.demo.dto.ReportExportResponse;
import com.example.demo.dto.ReportExportStatusResponse;
import com.example.demo.entity.ReportExport;
import com.example.demo.entity.ReportExportStatus;
import com.example.demo.repository.ReportExportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportExportSagaService {

    private static final Logger log = LoggerFactory.getLogger(ReportExportSagaService.class);
    private static final ParameterizedTypeReference<List<Map<String, Object>>> REPORT_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ReportExportRepository repository;
    private final RestTemplate restTemplate;
    private final CsvReportService csvReportService;
    private final String reportServiceUrl;
    private final ReportStorageQueueClient storageQueue;

    public ReportExportSagaService(
            ReportExportRepository repository,
            RestTemplate restTemplate,
            CsvReportService csvReportService,
            ReportStorageQueueClient storageQueue,
            @Value("${report.service.url:http://localhost:8081}") String reportServiceUrl) {
        this.repository = repository;
        this.restTemplate = restTemplate;
        this.csvReportService = csvReportService;
        this.reportServiceUrl = trimTrailingSlash(reportServiceUrl);
        this.storageQueue = storageQueue;
    }

    public ReportExportResponse exportCarsReport() {
        UUID exportId = UUID.randomUUID();
        String filename = createFilename(exportId);

        ReportExport saga = repository.saveAndFlush(
                new ReportExport(exportId, filename)
        );

        boolean uploadMayHaveReachedStorage = false;

        try {
            List<Map<String, Object>> rows = fetchCarsReport();
            saga.setRowCount(rows.size());
            saga.setStatus(ReportExportStatus.REPORT_RECEIVED);
            persist(saga);

            byte[] csvBytes = csvReportService.generate(rows);
            saga.setFileSizeBytes((long) csvBytes.length);
            saga.setStatus(ReportExportStatus.UPLOADING);
            persist(saga);

            uploadMayHaveReachedStorage = true;
            FileStorageResult stored = storageQueue.store(exportId, filename, csvBytes);

            saga.setStatus(ReportExportStatus.COMPLETED);
            saga.setCompletedAt(LocalDateTime.now());
            saga.setErrorMessage(null);
            persist(saga);

            log.info("Report export Saga completed exportId={} filename={} rows={} bytes={}",
                    exportId, filename, rows.size(), csvBytes.length);

            return new ReportExportResponse(
                    exportId,
                    stored.filename(),
                    stored.bucket(),
                    stored.sizeBytes(),
                    ReportExportStatus.COMPLETED.name(),
                    stored.savedAt()
            );
        } catch (Exception failure) {
            handleFailure(saga, uploadMayHaveReachedStorage, failure);
            log.error("Report export Saga failed exportId={} status={} reason={}",
                    exportId, saga.getStatus(), failure.getMessage(), failure);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Report export failed. exportId=" + exportId
                            + ", current status=" + saga.getStatus(),
                    failure
            );
        }
    }

    public ReportExportStatusResponse getStatus(UUID exportId) {
        return toStatusResponse(findExport(exportId));
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ReportExportStatusResponse retryCompensation(UUID exportId) {
        ReportExport saga = repository.findForUpdate(exportId)
                .orElseThrow(() -> exportNotFound(exportId));
        if (saga.getStatus() != ReportExportStatus.COMPENSATION_REQUIRED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Compensation is not required for export " + exportId
            );
        }

        try {
            storageQueue.delete(exportId, saga.getFilename());
        } catch (Exception exception) {
            saga.setErrorMessage(clip("Compensation retry failed: " + exception.getMessage()));
            persist(saga);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not complete compensation for export " + exportId,
                    exception
            );
        }
        saga.setStatus(ReportExportStatus.COMPENSATED);
        saga.setCompletedAt(null);
        persist(saga);
        log.info("Report export compensation completed exportId={} filename={}",
                exportId, saga.getFilename());
        return toStatusResponse(saga);
    }

    private List<Map<String, Object>> fetchCarsReport() {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                reportServiceUrl + "/reports/cars",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                REPORT_TYPE
        );
        if (response.getBody() == null) {
            throw new IllegalStateException("report-service returned an empty response body");
        }
        return response.getBody();
    }

    private void handleFailure(
            ReportExport saga,
            boolean uploadMayHaveReachedStorage,
            Exception originalFailure) {

        if (!uploadMayHaveReachedStorage) {
            saga.setStatus(ReportExportStatus.FAILED);
            saga.setErrorMessage(clip(originalFailure.getMessage()));
        } else {
            try {
                storageQueue.delete(saga.getId(), saga.getFilename());
                saga.setStatus(ReportExportStatus.COMPENSATED);
                saga.setErrorMessage(clip(
                        "Export failed; uploaded object was removed. Cause: "
                                + originalFailure.getMessage()
                ));
            } catch (Exception compensationFailure) {
                saga.setStatus(ReportExportStatus.COMPENSATION_REQUIRED);
                saga.setErrorMessage(clip(
                        "Export failed: " + originalFailure.getMessage()
                                + "; compensation failed: " + compensationFailure.getMessage()
                ));
                originalFailure.addSuppressed(compensationFailure);
            }
        }

        saga.setCompletedAt(null);
        try {
            persist(saga);
        } catch (Exception statePersistenceFailure) {
            originalFailure.addSuppressed(statePersistenceFailure);
            log.error("Cannot persist failed Saga state exportId={}", saga.getId(),
                    statePersistenceFailure);
        }
    }

    private void persist(ReportExport saga) {
        saga.setUpdatedAt(LocalDateTime.now());
        repository.saveAndFlush(saga);
    }

    private ReportExport findExport(UUID exportId) {
        return repository.findById(exportId).orElseThrow(() -> exportNotFound(exportId));
    }

    private ResponseStatusException exportNotFound(UUID exportId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Report export not found: " + exportId);
    }

    private ReportExportStatusResponse toStatusResponse(ReportExport saga) {
        return new ReportExportStatusResponse(
                saga.getId(),
                saga.getFilename(),
                saga.getStatus().name(),
                saga.getErrorMessage(),
                saga.getRowCount(),
                saga.getFileSizeBytes(),
                saga.getCreatedAt(),
                saga.getUpdatedAt(),
                saga.getCompletedAt()
        );
    }

    private String createFilename(UUID exportId) {
        return "cars-report-" + LocalDateTime.now().format(FILE_DATE_FORMAT)
                + "-" + exportId + ".csv";
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String clip(String value) {
        if (value == null || value.isBlank()) return "Unknown error";
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
