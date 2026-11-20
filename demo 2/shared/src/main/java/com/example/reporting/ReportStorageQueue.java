package com.example.reporting;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

public final class ReportStorageQueue {

    public static final String NAME = "reports.storage.commands";
    public static final String EXPORT_ID = "export-id";
    public static final String OPERATION = "operation";
    public static final String FILENAME = "filename";
    public static final String BUCKET = "bucket";
    public static final String SIZE_BYTES = "size-bytes";
    public static final String SAVED_AT = "saved-at";
    public static final String ERROR = "error";
    public static final String STORE = "STORE";
    public static final String DELETE = "DELETE";

    private ReportStorageQueue() {}

    public static Queue declare() {
        return QueueBuilder.durable(NAME)
                .withArgument("x-single-active-consumer", true)
                .build();
    }
}
