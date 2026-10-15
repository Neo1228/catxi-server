package com.catxi.chat.dto;

import java.time.LocalDateTime;

public record ChatSystemMessageEvent(
    Long roomId, Long messageId, String content, LocalDateTime sentAt) {}
