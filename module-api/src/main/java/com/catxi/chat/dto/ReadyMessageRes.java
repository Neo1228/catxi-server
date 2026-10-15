package com.catxi.chat.dto;

import com.catxi.chat.domain.ReadyType;
import java.time.LocalDateTime;

public record ReadyMessageRes(
    ReadyType type,
    Long roomId,
    Long senderId,
    String senderEmail,
    String senderName,
    String content,
    LocalDateTime sentAt) {
  public static ReadyMessageRes readyRequest(
      Long roomId, Long senderId, String senderEmail, String senderName) {
    return new ReadyMessageRes(
        ReadyType.READY_REQUEST,
        roomId,
        senderId,
        senderEmail,
        senderName,
        "방장이 준비 요청을 보냈습니다",
        LocalDateTime.now());
  }

  public static ReadyMessageRes readyAccept(
      Long roomId, Long senderId, String senderEmail, String senderName) {
    return new ReadyMessageRes(
        ReadyType.READY_ACCEPT,
        roomId,
        senderId,
        senderEmail,
        senderName,
        "참가자가 준비 요청을 수락했습니다",
        LocalDateTime.now());
  }

  public static ReadyMessageRes readyDeny(
      Long roomId, Long senderId, String senderEmail, String senderName) {
    return new ReadyMessageRes(
        ReadyType.READY_DENY,
        roomId,
        senderId,
        senderEmail,
        senderName,
        "참가자가 준비 요청을 거절했습니다",
        LocalDateTime.now());
  }
}
