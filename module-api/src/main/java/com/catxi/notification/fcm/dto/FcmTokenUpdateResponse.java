package com.catxi.notification.fcm.dto;

import java.time.LocalDateTime;

public record FcmTokenUpdateResponse(Long tokenId, LocalDateTime updatedAt, Boolean isActive) {}
