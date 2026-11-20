package com.example.storage.service;

import com.example.reporting.ReportStorageQueue;
import com.example.storage.dto.StoredFileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ReportStorageQueueListener {

    private static final Logger log = LoggerFactory.getLogger(ReportStorageQueueListener.class);
    private static final byte[] EMPTY_BODY = new byte[0];

    private final MinioStorageService storageService;

    public ReportStorageQueueListener(MinioStorageService storageService) {
        this.storageService = storageService;
    }

    @Bean
    public static Queue reportStorageCommands() {
        return ReportStorageQueue.declare();
    }

    @RabbitListener(queues = ReportStorageQueue.NAME)
    public Message handle(Message request) {
        String exportId = request.getMessageProperties().getHeader(ReportStorageQueue.EXPORT_ID);
        String operation = request.getMessageProperties().getHeader(ReportStorageQueue.OPERATION);
        String filename = request.getMessageProperties().getHeader(ReportStorageQueue.FILENAME);
        MessageBuilder reply = MessageBuilder.withBody(EMPTY_BODY);

        log.info("Received RabbitMQ command operation={} exportId={} filename={}",
                operation, exportId, filename);
        try {
            if (ReportStorageQueue.STORE.equals(operation)) {
                StoredFileResponse saved = storageService.save(filename, request.getBody());
                reply.setHeader(ReportStorageQueue.BUCKET, saved.bucket());
                reply.setHeader(ReportStorageQueue.SIZE_BYTES, saved.sizeBytes());
                reply.setHeader(ReportStorageQueue.SAVED_AT, saved.savedAt().toString());
            } else if (ReportStorageQueue.DELETE.equals(operation)) {
                storageService.delete(filename);
                log.info("CSV file deleted from MinIO as Saga compensation exportId={} filename={}",
                        exportId, filename);
            } else {
                throw new IllegalArgumentException("Unknown storage command: " + operation);
            }
        } catch (RuntimeException failure) {
            log.warn("RabbitMQ storage command failed operation={} exportId={} filename={}",
                    operation, exportId, filename, failure);
            reply.setHeader(ReportStorageQueue.ERROR, failure.toString());
        }
        return reply.build();
    }
}
