package com.catxi.auth.infra;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepository {

  private static final String PREFIX = "refresh:";
  private static final DefaultRedisScript<Long> ROTATE_SCRIPT =
      new DefaultRedisScript<>(
          "local current = redis.call('GET', KEYS[1]) "
              + "if current == ARGV[1] then "
              + "redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3]) "
              + "return 1 "
              + "end "
              + "return 0",
          Long.class);
  private static final DefaultRedisScript<Long> DELETE_IF_MATCHES_SCRIPT =
      new DefaultRedisScript<>(
          "if redis.call('GET', KEYS[1]) == ARGV[1] then "
              + "return redis.call('DEL', KEYS[1]) "
              + "end "
              + "return 0",
          Long.class);

  private final RedisTemplate<String, String> tokenRedisTemplate;

  public RefreshTokenRepository(
      @Qualifier("tokenRedisTemplate") RedisTemplate<String, String> tokenRedisTemplate) {
    this.tokenRedisTemplate = tokenRedisTemplate;
  }

  public void save(String key, String token, Duration ttl) {
    tokenRedisTemplate.opsForValue().set(PREFIX + key, token, ttl);
  }

  public Optional<String> findByKey(String key) {
    return Optional.ofNullable(tokenRedisTemplate.opsForValue().get(PREFIX + key));
  }

  public boolean isValid(String key, String token) {
    return findByKey(key).map(saved -> saved.equals(token)).orElse(false);
  }

  public void delete(String key) {
    tokenRedisTemplate.delete(PREFIX + key);
  }

  public boolean deleteIfMatches(String key, String token) {
    Long deleted =
        tokenRedisTemplate.execute(DELETE_IF_MATCHES_SCRIPT, List.of(PREFIX + key), token);
    return Long.valueOf(1).equals(deleted);
  }

  // Redis에서 토큰 비교와 교체를 원자적으로 처리해 동시 갱신 요청 중 하나만 성공시킨다.
  public boolean rotate(String key, String oldToken, String newToken, Duration ttl) {
    long ttlMillis = ttl.toMillis();
    if (ttlMillis <= 0) {
      throw new IllegalArgumentException("Refresh token TTL must be positive");
    }

    Long rotated =
        tokenRedisTemplate.execute(
            ROTATE_SCRIPT, List.of(PREFIX + key), oldToken, newToken, Long.toString(ttlMillis));
    return Long.valueOf(1).equals(rotated);
  }
}
