package com.catxi.chat.map.realtime;

import com.catxi.chat.map.domain.MapCoordinate;
import com.catxi.common.api.error.CommonErrorCode;
import com.catxi.common.api.exception.CatxiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapCoordinatePublisher {

  private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public void publish(MapCoordinate coordinate) {
    try {
      MapCoordinateBroadcast message =
          new MapCoordinateBroadcast(
              coordinate.roomId(),
              coordinate.email(),
              coordinate.memberName(),
              coordinate.nickname(),
              coordinate.latitude(),
              coordinate.longitude(),
              coordinate.distance());
      redisTemplate.convertAndSend("map", objectMapper.writeValueAsString(message));
    } catch (JacksonException exception) {
      log.error("좌표 실시간 전송 메시지를 만들지 못했습니다. roomId={}", coordinate.roomId(), exception);
      throw new CatxiException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
  }
}
