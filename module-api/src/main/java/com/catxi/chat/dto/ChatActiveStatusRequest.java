package com.catxi.chat.dto;

import jakarta.validation.constraints.NotNull;

public record ChatActiveStatusRequest(
    @NotNull(message = "방 ID는 필수입니다.") Long roomId,
    @NotNull(message = "활성 상태는 필수입니다.") Boolean isActive) {}
