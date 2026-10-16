package com.catxi.notification.fcm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FcmFidUpdateRequest(
    @NotBlank(message = "FCM 설치 ID는 필수입니다.") @Size(max = 255, message = "FCM 설치 ID가 너무 깁니다.")
        String fid) {}
