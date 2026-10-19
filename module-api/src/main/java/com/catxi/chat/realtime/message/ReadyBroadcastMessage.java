package com.catxi.chat.realtime.message;

import com.catxi.chat.domain.ReadyType;
import java.time.LocalDateTime;

public record ReadyBroadcastMessage(
    ReadyType type,
    Long roomId,
    Long senderId,
    String senderEmail,
    String senderName,
    String content,
    LocalDateTime sentAt) {
  public static ReadyBroadcastMessage readyRequest(
      Long roomId, Long senderId, String senderEmail, String senderName) {
    return new ReadyBroadcastMessage(
        ReadyType.READY_REQUEST,
        roomId,
        senderId,
        senderEmail,
        senderName,
        "방장이 준비 요청을 보냈습니다",
        LocalDateTime.now());
  }

  public static ReadyBroadcastMessage readyAccept(
      Long roomId, Long senderId, String senderEmail, String senderName) {
    return new ReadyBroadcastMessage(
        ReadyType.READY_ACCEPT,
        roomId,
        senderId,
        senderEmail,
        senderName,
        "참가자가 준비 요청을 수락했습니다",
        LocalDateTime.now());
  }

  public static ReadyBroadcastMessage readyDeny(
      Long roomId, Long senderId, String senderEmail, String senderName) {
    return new ReadyBroadcastMessage(
        ReadyType.READY_DENY,
        roomId,
        senderId,
        senderEmail,
        senderName,
        "참가자가 준비 요청을 거절했습니다",
        LocalDateTime.now());
  }
}
