package com.example.storage.controller;

import com.example.storage.dto.StoredFileResponse;
import com.example.storage.service.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileStorageController {

    private static final Logger log = LoggerFactory.getLogger(FileStorageController.class);
    private final MinioStorageService storageService;

    public FileStorageController(MinioStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(consumes = {"text/csv", "application/octet-stream"})
    public ResponseEntity<StoredFileResponse> upload(
            @RequestHeader("X-Filename") String filename,
            @RequestBody byte[] content) {
        StoredFileResponse saved = storageService.save(filename, content);
        log.info("CSV file stored filename={} bucket={} sizeBytes={} instance={}",
                saved.filename(), saved.bucket(), saved.sizeBytes(), instanceId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<String> list() {
        List<String> files = storageService.list();
        log.info("MinIO CSV objects listed bucket={} count={} instance={}",
                storageService.bucketName(), files.size(), instanceId());
        return files;
    }

    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> download(@PathVariable String filename) {
        byte[] content = storageService.read(filename);
        log.info("CSV file downloaded from MinIO filename={} sizeBytes={} instance={}",
                filename, content.length, instanceId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    private String instanceId() {
        String hostname = System.getenv("HOSTNAME");
        return hostname != null ? hostname : "local";
    }
}
