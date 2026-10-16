package com.catxi.chat.realtime.dto;

import com.catxi.chat.domain.ReadyType;
import java.time.LocalDateTime;

public record ReadyMessageResponse(
    ReadyType type,
    Long roomId,
    Long senderId,
    String senderEmail,
    String senderName,
    String content,
    LocalDateTime sentAt) {
  public static ReadyMessageResponse readyRequest(
      Long roomId, Long senderId, String senderEmail, String senderName) {
    return new ReadyMessageResponse(
        ReadyType.READY_REQUEST,
        roomId,
        senderId,
        senderEmail,
        senderName,
        "방장이 준비 요청을 보냈습니다",
        LocalDateTime.now());
  }

  public static ReadyMessageResponse readyAccept(
      Long roomId, Long senderId, String senderEmail, String senderName) {
    return new ReadyMessageResponse(
        ReadyType.READY_ACCEPT,
        roomId,
        senderId,
        senderEmail,
        senderName,
        "참가자가 준비 요청을 수락했습니다",
        LocalDateTime.now());
  }

  public static ReadyMessageResponse readyDeny(
      Long roomId, Long senderId, String senderEmail, String senderName) {
    return new ReadyMessageResponse(
        ReadyType.READY_DENY,
        roomId,
        senderId,
        senderEmail,
        senderName,
        "참가자가 준비 요청을 거절했습니다",
        LocalDateTime.now());
  }
}
