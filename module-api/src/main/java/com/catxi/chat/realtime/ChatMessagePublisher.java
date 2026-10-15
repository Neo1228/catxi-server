package com.catxi.chat.realtime;

import com.catxi.chat.dto.ChatMessageBroadcast;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessagePublisher {

  private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messageTemplate;

  public void publish(Long roomId, ChatMessageBroadcast message) {
    try {
      String payload = objectMapper.writeValueAsString(message);
      Long subscribers = redisTemplate.convertAndSend("chat", payload);
      if (subscribers == null || subscribers == 0) {
        publishLocally(roomId, message);
      }
    } catch (JacksonException | DataAccessException exception) {
      log.warn(
          "채팅 메시지 실시간 발행 실패; 저장된 메시지는 이력 조회로 복구할 수 있습니다. roomId={}, messageId={}",
          roomId,
          message.messageId(),
          exception);
      publishLocally(roomId, message);
    }
  }

  public void acknowledge(String senderEmail, Long roomId, ChatMessageBroadcast message) {
    try {
      messageTemplate.convertAndSendToUser(senderEmail, "/queue/chat-ack", message);
    } catch (RuntimeException exception) {
      log.warn(
          "채팅 메시지는 저장됐지만 보낸 사람에게 확인 응답을 전달하지 못했습니다. roomId={}, messageId={}",
          roomId,
          message.messageId(),
          exception);
    }
  }

  private void publishLocally(Long roomId, ChatMessageBroadcast message) {
    try {
      messageTemplate.convertAndSend("/topic/" + roomId, message);
    } catch (RuntimeException exception) {
      log.error(
          "저장된 채팅 메시지 실시간 전달이 실패했습니다. roomId={}, messageId={}",
          roomId,
          message.messageId(),
          exception);
    }
  }
}
