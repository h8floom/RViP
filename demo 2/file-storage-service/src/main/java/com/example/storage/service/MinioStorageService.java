package com.example.storage.service;

import com.example.storage.dto.StoredFileResponse;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Pattern SAFE_CSV_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,110}\\.csv");

    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorageService(
            MinioClient minioClient,
            @Value("${minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    /** Retry bucket creation because Compose starts MinIO and Java services concurrently. */
    public void initializeBucketWithRetry() {
        Exception lastError = null;
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("MinIO bucket created bucket={}", bucket);
                } else {
                    log.info("MinIO bucket is ready bucket={}", bucket);
                }
                return;
            } catch (Exception exception) {
                lastError = exception;
                log.warn("Waiting for MinIO bucket initialization attempt={}/30 reason={}",
                        attempt, exception.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for MinIO", interrupted);
                }
            }
        }
        throw new IllegalStateException(
                "Cannot initialize MinIO bucket '" + bucket + "'. Check MinIO endpoint and credentials.",
                lastError);
    }

    public StoredFileResponse save(String filename, byte[] content) {
        validateFilename(filename);
        if (content == null || content.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file must not be empty");
        }
        if (content.length > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Maximum file size is 10 MiB");
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(filename)
                            .stream(input, content.length, -1)
                            .contentType("text/csv; charset=UTF-8")
                            .build());
            log.info("CSV object uploaded to MinIO bucket={} object={} sizeBytes={}",
                    bucket, filename, content.length);
            return new StoredFileResponse(filename, bucket, content.length, "SAVED", Instant.now());
        } catch (Exception exception) {
            log.error("Cannot upload CSV to MinIO bucket={} object={}", bucket, filename, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cannot save CSV in MinIO", exception);
        }
    }

    public byte[] read(String filename) {
        validateFilename(filename);
        try (InputStream input = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(filename).build())) {
            return input.readAllBytes();
        } catch (ErrorResponseException exception) {
            String code = exception.errorResponse() != null ? exception.errorResponse().code() : "";
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CSV file not found: " + filename);
            }
            log.error("MinIO rejected download bucket={} object={}", bucket, filename, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MinIO rejected the file download", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Cannot download CSV from MinIO bucket={} object={}", bucket, filename, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cannot read CSV from MinIO", exception);
        }
    }

    public List<String> list() {
        List<String> filenames = new ArrayList<>();
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).recursive(true).build());
            for (Result<Item> result : objects) {
                Item item = result.get();
                if (!item.isDir() && item.objectName() != null && item.objectName().endsWith(".csv")) {
                    filenames.add(item.objectName());
                }
            }
            return filenames.stream().sorted().toList();
        } catch (Exception exception) {
            log.error("Cannot list CSV objects in MinIO bucket={}", bucket, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cannot list files in MinIO", exception);
        }
    }

    public String bucketName() {
        return bucket;
    }

    private void validateFilename(String filename) {
        if (filename == null || !SAFE_CSV_NAME.matcher(filename).matches() || filename.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Filename must be a simple .csv filename");
        }
    }
}
