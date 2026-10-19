package com.catxi.chat.service;

import com.catxi.chat.broadcast.message.ChatMessageBroadcast;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageDeliveryService {

  private final ChatMessageService chatMessageService;
  private final SimpMessagingTemplate messageTemplate;

  public void sendMessage(Long roomId, String senderEmail, String message, UUID clientMessageId) {
    ChatMessageSendResult savedMessage =
        chatMessageService.saveMessage(roomId, senderEmail, message, clientMessageId);
    ChatMessageBroadcast acknowledgement =
        new ChatMessageBroadcast(
            roomId,
            senderEmail,
            message,
            savedMessage.sentAt(),
            savedMessage.messageId(),
            savedMessage.clientMessageId());

    try {
      messageTemplate.convertAndSendToUser(senderEmail, "/queue/chat-ack", acknowledgement);
    } catch (RuntimeException exception) {
      log.warn(
          "채팅 메시지는 저장됐지만 보낸 사람에게 확인 응답을 전달하지 못했습니다. roomId={}, messageId={}",
          roomId,
          savedMessage.messageId(),
          exception);
    }
  }
}
