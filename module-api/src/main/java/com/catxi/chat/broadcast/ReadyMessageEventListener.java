package com.catxi.chat.broadcast;

import com.catxi.chat.broadcast.message.ReadyBroadcastMessage;
import com.catxi.chat.event.ReadyMessageEvent;
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
public class ReadyMessageEventListener {

  private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReadyMessageEvent(ReadyMessageEvent event) {
    try {
      String channel = ChatRedisChannels.READY_PREFIX + event.roomId();
      String json = objectMapper.writeValueAsString(toResponse(event));
      redisTemplate.convertAndSend(channel, json);
    } catch (Exception e) {
      log.warn("준비 상태 실시간 알림 발행에 실패했습니다: roomId={}", event.roomId(), e);
    }
  }

  private ReadyBroadcastMessage toResponse(ReadyMessageEvent event) {
    return switch (event.type()) {
      case READY_REQUEST ->
          ReadyBroadcastMessage.readyRequest(
              event.roomId(), event.senderId(), event.senderEmail(), event.senderName());
      case READY_ACCEPT ->
          ReadyBroadcastMessage.readyAccept(
              event.roomId(), event.senderId(), event.senderEmail(), event.senderName());
      case READY_DENY ->
          ReadyBroadcastMessage.readyDeny(
              event.roomId(), event.senderId(), event.senderEmail(), event.senderName());
    };
  }
}
