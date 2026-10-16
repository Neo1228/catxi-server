package com.catxi.chat.event;

import com.catxi.chat.domain.ReadyType;

public record ReadyMessageEvent(
    Long roomId, ReadyType type, Long senderId, String senderEmail, String senderName) {}
