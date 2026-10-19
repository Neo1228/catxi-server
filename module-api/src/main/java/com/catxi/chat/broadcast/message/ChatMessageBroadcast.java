package com.catxi.chat.broadcast.message;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageBroadcast(
    Long roomId,
    String email,
    String message,
    LocalDateTime sentAt,
    Long messageId,
    UUID clientMessageId) {

  public ChatMessageBroadcast(
      Long roomId, String email, String message, LocalDateTime sentAt, Long messageId) {
    this(roomId, email, message, sentAt, messageId, null);
  }
}
