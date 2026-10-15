package com.catxi.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessagePageRes(
    List<ChatMessageRes> messages,
    LocalDateTime nextCreatedAt,
    Long nextMessageId,
    boolean hasMore) {

  public ChatMessagePageRes {
    messages = List.copyOf(messages);
  }
}
