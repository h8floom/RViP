package com.example.demo.dto;

import java.time.Instant;

public record FileStorageResult(
        String filename,
        String bucket,
        long sizeBytes,
        Instant savedAt
) {
}
