package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class ReportDownloadService {

    private static final Logger log = LoggerFactory.getLogger(ReportDownloadService.class);
    private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");

    private final RestTemplate storageClient;
    private final Executor downloadExecutor;
    private final String storageUrl;

    public ReportDownloadService(
            @Qualifier("reportDownloadRestTemplate") RestTemplate storageClient,
            @Qualifier("reportDownloadExecutor") Executor downloadExecutor,
            @Value("${file.storage.service-url:http://localhost:8082}") String storageUrl) {
        this.storageClient = storageClient;
        this.downloadExecutor = downloadExecutor;
        this.storageUrl = storageUrl;
    }

    public CompletableFuture<ResponseEntity<byte[]>> download(String filename) {
        try {
            return CompletableFuture.supplyAsync(() -> fetchFile(filename), downloadExecutor);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Report download workers are busy", exception));
        }
    }

    private ResponseEntity<byte[]> fetchFile(String filename) {
        URI uri = UriComponentsBuilder.fromUriString(storageUrl)
                .pathSegment("api", "files", "{filename}")
                .encode()
                .buildAndExpand(filename)
                .toUri();
        log.info("Downloading report filename={}", filename);

        byte[] file;
        try {
            file = storageClient.getForObject(uri, byte[].class);
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report file not found");
            }
            if (exception.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report filename");
            }
            throw storageFailure(exception);
        } catch (RestClientException exception) {
            throw storageFailure(exception);
        }

        if (file == null) {
            throw storageFailure(null);
        }
        log.info("Downloaded report filename={} bytes={}", filename, file.length);
        return ResponseEntity.ok()
                .contentType(CSV)
                .headers(headers -> headers.setContentDisposition(ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build()))
                .body(file);
    }

    private ResponseStatusException storageFailure(RestClientException exception) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Could not download the report from file storage", exception);
    }
}
