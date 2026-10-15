package com.catxi.chat.listener;

import com.catxi.chat.dto.ReadyMessageEvent;
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
      String json = objectMapper.writeValueAsString(event.readyMessageRes());
      redisTemplate.convertAndSend(event.channel(), json);
    } catch (Exception e) {
      log.warn("준비 상태 실시간 알림 발행에 실패했습니다: channel={}", event.channel(), e);
    }
  }
}
