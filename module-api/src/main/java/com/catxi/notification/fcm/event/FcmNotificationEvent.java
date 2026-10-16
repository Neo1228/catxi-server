package com.catxi.notification.fcm.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

  public static FcmNotificationEvent createChatMessage(
      Long targetMemberId, Long roomId, Long messageId) {
    return new FcmNotificationEvent(
        UUID.randomUUID().toString(),
        NotificationType.CHAT_MESSAGE,
        List.of(targetMemberId),
        "새로운 채팅 메시지",
        "새로운 채팅 메시지가 도착했습니다",
        Map.of(
            "type",
            NotificationType.CHAT_MESSAGE.payloadType(),
            "roomId",
            roomId.toString(),
            "messageId",
            messageId.toString()),
        LocalDateTime.now(),
        0);
  }

  public static FcmNotificationEvent createReadyRequest(List<Long> targetMemberIds, Long roomId) {
    return new FcmNotificationEvent(
        UUID.randomUUID().toString(),
        NotificationType.READY_REQUEST,
        targetMemberIds,
        "준비 요청",
        "방장이 준비 요청을 보냈습니다",
        Map.of("type", NotificationType.READY_REQUEST.payloadType(), "roomId", roomId.toString()),
        LocalDateTime.now(),
        0);
  }

  public static FcmNotificationEvent createMatchCompleted(List<Long> targetMemberIds, Long roomId) {
    return new FcmNotificationEvent(
        UUID.randomUUID().toString(),
        NotificationType.MATCH_COMPLETED,
        targetMemberIds,
        "매칭 완료",
        "모든 참가자가 준비되어 매칭이 완료되었습니다",
        Map.of("type", NotificationType.MATCH_COMPLETED.payloadType(), "roomId", roomId.toString()),
        LocalDateTime.now(),
        0);
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
