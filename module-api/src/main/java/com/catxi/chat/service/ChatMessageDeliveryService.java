package com.catxi.chat.service;

import com.catxi.chat.broadcast.ChatMessagePublisher;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageDeliveryService {

  private final ChatMessageService chatMessageService;
  private final ChatMessagePublisher chatMessagePublisher;

  public void sendMessage(Long roomId, String senderEmail, String content, UUID clientMessageId) {
    var savedMessage =
        chatMessageService.saveMessage(roomId, senderEmail, content, clientMessageId);
    chatMessagePublisher.deliver(
        roomId,
        senderEmail,
        content,
        savedMessage.sentAt(),
        savedMessage.messageId(),
        savedMessage.clientMessageId());
  }
}
