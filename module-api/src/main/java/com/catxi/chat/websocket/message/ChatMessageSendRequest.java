package com.catxi.chat.websocket.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ChatMessageSendRequest(
    @NotBlank @Size(max = 500) String message, UUID clientMessageId) {}
