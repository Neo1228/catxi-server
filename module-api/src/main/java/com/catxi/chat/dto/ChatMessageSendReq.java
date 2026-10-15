package com.catxi.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageSendReq(
    Long roomId,
    String email,
    @NotBlank @Size(max = 500) String message,
    LocalDateTime sentAt,
    UUID clientMessageId) {}
