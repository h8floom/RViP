package com.example.demo.dto;

import java.time.Instant;

/** JSON response returned by file-storage-service after an object was saved to MinIO. */
public record FileStorageResult(
        String filename,
        String bucket,
        long sizeBytes,
        String status,
        Instant savedAt
) {
}
