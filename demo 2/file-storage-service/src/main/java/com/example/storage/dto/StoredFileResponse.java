package com.example.storage.dto;

import java.time.Instant;

public record StoredFileResponse(
        String filename,
        String bucket,
        long sizeBytes,
        String status,
        Instant savedAt
) {
}
