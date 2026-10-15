package com.catxi.report.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record ReportCreateRes(
    Long reportId,
    Long reportedUserId,
    String reportedUserNickname,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime reportedAt) {}
