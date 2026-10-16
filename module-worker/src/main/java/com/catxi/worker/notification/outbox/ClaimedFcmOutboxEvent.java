package com.catxi.worker.notification.outbox;

public record ClaimedFcmOutboxEvent(
    long id,
    String eventId,
    String eventType,
    String payload,
    int retryCount,
    String status,
    String publicationKey) {}
