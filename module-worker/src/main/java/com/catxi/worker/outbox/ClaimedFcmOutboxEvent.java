package com.catxi.worker.outbox;

public record ClaimedFcmOutboxEvent(
    long id, String eventId, String eventType, String payload, int retryCount) {}
