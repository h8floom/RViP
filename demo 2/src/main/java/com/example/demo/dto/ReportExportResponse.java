package com.example.demo.dto;

import java.time.Instant;
import java.util.UUID;

/** Ответ на успешно завершённый экспорт отчёта. */
public record ReportExportResponse(
        UUID exportId,
        String filename,
        String bucket,
        long sizeBytes,
        String status,
        Instant savedAt
) {
}
