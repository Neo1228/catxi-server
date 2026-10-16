package com.catxi.chat.service;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageSendResult(
    Long messageId, LocalDateTime sentAt, UUID clientMessageId, boolean created) {}
