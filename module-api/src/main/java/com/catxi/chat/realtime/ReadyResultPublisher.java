package com.catxi.chat.realtime;

import com.catxi.chat.realtime.message.RoomEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadyResultPublisher {

  private final RedisTemplate<String, String> redisTemplate;
  private final ObjectMapper objectMapper;

  public void publish(Long roomId, String type, String content) {
    try {
      RoomEventMessage event = new RoomEventMessage(roomId, type, content);
      redisTemplate.convertAndSend("readyresult:" + roomId, objectMapper.writeValueAsString(event));
    } catch (Exception exception) {
      log.error("준비 결과 발행 실패: roomId={}", roomId, exception);
    }
  }
}
