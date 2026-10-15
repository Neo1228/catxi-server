package com.catxi.report.event;

import java.time.LocalDateTime;

public record ReportCreatedEvent(Long reportId, Long roomId, LocalDateTime createdAt) {}
