package com.catxi.chat.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageSendResult(
    Long messageId, LocalDateTime sentAt, UUID clientMessageId, boolean created) {}
