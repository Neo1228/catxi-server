package com.catxi.auth.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class OAuthCodeCache {
  private static final String PREFIX = "oauth:used-code:";
  private static final Duration CODE_TTL = Duration.ofMinutes(5);

  private final RedisTemplate<String, String> tokenRedisTemplate;

  public OAuthCodeCache(
      @Qualifier("tokenRedisTemplate") RedisTemplate<String, String> tokenRedisTemplate) {
    this.tokenRedisTemplate = tokenRedisTemplate;
  }

  // 인가 코드를 Redis에서 원자적으로 선점해 여러 인스턴스의 중복 로그인을 막습니다.
  public boolean markIfUnused(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("OAuth authorization code must not be blank");
    }

    String key = PREFIX + hash(code);
    return Boolean.TRUE.equals(tokenRedisTemplate.opsForValue().setIfAbsent(key, "1", CODE_TTL));
  }

  private String hash(String code) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
