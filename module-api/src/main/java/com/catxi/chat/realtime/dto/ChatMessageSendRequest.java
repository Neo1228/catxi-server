package com.catxi.chat.realtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ChatMessageSendRequest(
    @NotBlank @Size(max = 500) String message, UUID clientMessageId) {}
