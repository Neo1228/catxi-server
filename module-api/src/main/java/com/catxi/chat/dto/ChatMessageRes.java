package com.catxi.chat.dto;

import java.time.LocalDateTime;

public record ChatMessageRes(
    String senderEmail,
    Long messageId,
    Long roomId,
    Long senderId,
    String senderName,
    String content,
    LocalDateTime sentAt) {}
