package com.catxi.chat.broadcast;

import com.catxi.chat.broadcast.message.ChatMessageBroadcast;
import com.catxi.chat.event.ChatSystemMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatSystemMessageEventListener {
  private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ChatSystemMessageEvent event) {
    try {
      ChatMessageBroadcast message =
          new ChatMessageBroadcast(
              event.roomId(), "[SYSTEM]", event.content(), event.sentAt(), event.messageId());
      redisTemplate.convertAndSend(
          ChatRedisChannels.CHAT, objectMapper.writeValueAsString(message));
    } catch (Exception e) {
      log.error("시스템 메시지 발행 실패: roomId={}", event.roomId(), e);
    }
  }
}
