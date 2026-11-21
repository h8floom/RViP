package com.example.demo.service;

import com.example.demo.dto.FileStorageResult;
import com.example.reporting.ReportStorageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ReportStorageQueueClient {

    private static final Logger log = LoggerFactory.getLogger(ReportStorageQueueClient.class);
    private static final byte[] EMPTY_BODY = new byte[0];

    private final RabbitTemplate rabbitTemplate;

    public ReportStorageQueueClient(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Bean
    public static Queue reportStorageCommands() {
        return ReportStorageQueue.declare();
    }

    public FileStorageResult store(UUID exportId, String filename, byte[] content) {
        Message reply = send(exportId, ReportStorageQueue.STORE, filename, content);
        return new FileStorageResult(filename,
                reply.getMessageProperties().getHeader(ReportStorageQueue.BUCKET),
                reply.getMessageProperties().<Number>getHeader(ReportStorageQueue.SIZE_BYTES).longValue(),
                Instant.parse(reply.getMessageProperties().getHeader(ReportStorageQueue.SAVED_AT)));
    }

    public void delete(UUID exportId, String filename) {
        send(exportId, ReportStorageQueue.DELETE, filename, EMPTY_BODY);
    }

    private Message send(UUID exportId, String operation, String filename, byte[] content) {
        MessageBuilder command = MessageBuilder.withBody(content);
        command.setContentType(ReportStorageQueue.STORE.equals(operation)
                        ? "text/csv; charset=UTF-8" : "application/octet-stream")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader(ReportStorageQueue.EXPORT_ID, exportId.toString())
                .setHeader(ReportStorageQueue.OPERATION, operation)
                .setHeader(ReportStorageQueue.FILENAME, filename);
        if (ReportStorageQueue.STORE.equals(operation)) {
            command.setExpiration("25000");
        }

        log.info("Sending RabbitMQ command operation={} exportId={} filename={} queue={}",
                operation, exportId, filename, ReportStorageQueue.NAME);
        Message reply = rabbitTemplate.sendAndReceive("", ReportStorageQueue.NAME, command.build());
        if (reply == null) {
            throw new IllegalStateException("RabbitMQ reply timeout for " + operation + " " + filename);
        }
        String error = reply.getMessageProperties().getHeader(ReportStorageQueue.ERROR);
        if (error != null) {
            throw new IllegalStateException("File storage " + operation + " failed: " + error);
        }
        log.info("Received RabbitMQ reply operation={} exportId={} filename={}", operation, exportId, filename);
        return reply;
    }
}
