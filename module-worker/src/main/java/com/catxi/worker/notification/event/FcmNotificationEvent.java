package com.catxi.worker.notification.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record FcmNotificationEvent(
    String eventId,
    NotificationType type,
    List<Long> targetMemberIds,
    String title,
    String body,
    Map<String, String> data,
    LocalDateTime createdAt,
    int retryCount) {

  public FcmNotificationEvent {
    targetMemberIds = List.copyOf(targetMemberIds);
    data = Map.copyOf(data);
  }

  public FcmNotificationEvent withIncrementedRetry() {
    return new FcmNotificationEvent(
        eventId,
        type,
        targetMemberIds,
        title,
        body,
        data,
        createdAt,
        Math.incrementExact(retryCount));
  }

  public FcmNotificationEvent forReplay(String replayEventId) {
    return new FcmNotificationEvent(
        replayEventId, type, targetMemberIds, title, body, data, createdAt, 0);
  }

  public enum NotificationType {
    CHAT_MESSAGE("CHAT"),
    READY_REQUEST("READY_REQUEST"),
    MATCH_COMPLETED("MATCH_COMPLETED");

    private final String payloadType;

    NotificationType(String payloadType) {
      this.payloadType = payloadType;
    }

    public String payloadType() {
      return payloadType;
    }
  }
}
