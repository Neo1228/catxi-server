package com.catxi.chat.map.repository;

import com.catxi.chat.map.error.MapErrorCode;
import com.catxi.common.api.exception.CatxiException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CoordinateRepository {
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private static final long GEO_TTL_SECONDS = 300;

  public Map<String, Double> getCoordinate(Long roomId, String email) {
    String key = "map:" + roomId + ":" + email;
    String json = redisTemplate.opsForValue().get(key);

    if (json == null) {
      return null;
    }

    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      log.warn("[좌표 파싱 실패] roomId: {}", roomId);
      return null;
    }
  }

  public Map<String, Double> getDeparture(Long roomId) {
    String key = "map:" + roomId + ":" + "departure";
    String json = redisTemplate.opsForValue().get(key);

    if (json == null) {
      return null;
    }

    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      log.warn("[출발지 좌표 파싱 실패] roomId: {}", roomId);
      return null;
    }
  }

  public void saveCoordinateWithDistance(
      Long roomId, String email, Double latitude, Double longitude, Double distance) {
    String key = "map:" + roomId + ":" + email;
    Map<String, Double> coordMap = new HashMap<>();
    coordMap.put("latitude", latitude);
    coordMap.put("longitude", longitude);
    coordMap.put("distance", distance);

    try {
      String json = objectMapper.writeValueAsString(coordMap);
      redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(GEO_TTL_SECONDS));
    } catch (Exception e) {
      log.error("[좌표 저장 실패] roomId: {}", roomId);
      throw new CatxiException(MapErrorCode.COORDINATE_SAVE_FAILED);
    }
  }

  public void saveDeparture(Long roomId, Double latitude, Double longitude) {
    String key = "map:" + roomId + ":" + "departure";
    Map<String, Object> coordMap = new HashMap<>();
    coordMap.put("latitude", latitude);
    coordMap.put("longitude", longitude);

    try {
      String json = objectMapper.writeValueAsString(coordMap);
      redisTemplate.opsForValue().set(key, json, Duration.ofHours(48)); // TTL 48시간
    } catch (Exception e) {
      throw new CatxiException(MapErrorCode.COORDINATE_SAVE_FAILED);
    }
  }
}
