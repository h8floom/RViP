package com.example.demo.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Текущее сохранённое состояние Saga. */
public record ReportExportStatusResponse(
        UUID exportId,
        String filename,
        String status,
        String errorMessage,
        Integer rowCount,
        Long fileSizeBytes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
) {
}
