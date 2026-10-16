package com.catxi.chat.event;

public record ChatMessageNotificationRequested(Long targetMemberId, Long roomId, Long messageId) {}
