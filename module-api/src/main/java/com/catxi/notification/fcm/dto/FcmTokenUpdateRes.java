package com.catxi.notification.fcm.dto;

import java.time.LocalDateTime;

public record FcmTokenUpdateRes(Long tokenId, LocalDateTime updatedAt, Boolean isActive) {}
