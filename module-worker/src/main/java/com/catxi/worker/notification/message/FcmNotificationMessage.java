package com.catxi.worker.notification.message;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record FcmNotificationMessage(
    String eventId,
    NotificationType type,
    List<Long> targetMemberIds,
    String title,
    String body,
    Map<String, String> data,
    LocalDateTime createdAt,
    int retryCount) {

  public FcmNotificationMessage {
    targetMemberIds = List.copyOf(targetMemberIds);
    data = Map.copyOf(data);
  }

  public FcmNotificationMessage withIncrementedRetry() {
    return new FcmNotificationMessage(
        eventId,
        type,
        targetMemberIds,
        title,
        body,
        data,
        createdAt,
        Math.incrementExact(retryCount));
  }

  public FcmNotificationMessage forReplay(String replayEventId) {
    return new FcmNotificationMessage(
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
