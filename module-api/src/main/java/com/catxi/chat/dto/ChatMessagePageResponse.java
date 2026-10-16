package com.catxi.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessagePageResponse(
    List<ChatMessageResponse> messages,
    LocalDateTime nextCreatedAt,
    Long nextMessageId,
    boolean hasMore) {

  public ChatMessagePageResponse {
    messages = List.copyOf(messages);
  }
}
