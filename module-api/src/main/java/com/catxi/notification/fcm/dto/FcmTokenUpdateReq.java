package com.catxi.notification.fcm.dto;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenUpdateReq(@NotBlank(message = "FCM 토큰은 필수입니다.") String token) {}
