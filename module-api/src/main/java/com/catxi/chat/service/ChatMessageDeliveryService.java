package com.catxi.chat.service;

import com.catxi.chat.dto.ChatMessageBroadcast;
import com.catxi.chat.dto.ChatMessageSendReq;
import com.catxi.chat.realtime.ChatMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageDeliveryService {

  private final ChatMessageService chatMessageService;
  private final ChatMessagePublisher chatMessagePublisher;

  public void sendMessage(Long roomId, String senderEmail, ChatMessageSendReq request) {
    var savedMessage = chatMessageService.saveMessage(roomId, senderEmail, request);
    ChatMessageBroadcast message =
        new ChatMessageBroadcast(
            roomId,
            senderEmail,
            request.message(),
            savedMessage.sentAt(),
            savedMessage.messageId(),
            savedMessage.clientMessageId());
    chatMessagePublisher.acknowledge(senderEmail, roomId, message);
    chatMessagePublisher.publish(roomId, message);
  }
}
