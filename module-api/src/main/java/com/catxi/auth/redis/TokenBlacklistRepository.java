package com.catxi.auth.redis;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TokenBlacklistRepository {

  private final RedisTemplate<String, String> tokenRedisTemplate;

  public TokenBlacklistRepository(
      @Qualifier("tokenRedisTemplate") RedisTemplate<String, String> tokenRedisTemplate) {
    this.tokenRedisTemplate = tokenRedisTemplate;
  }

  private static final String TOKEN_BLACKLIST_PREFIX = "blacklist:token:";
  private static final String USER_BLACKLIST_PREFIX = "blacklist:user:";

  public void addTokenToBlacklist(String accessToken, Duration ttl) {
    tokenRedisTemplate.opsForValue().set(TOKEN_BLACKLIST_PREFIX + accessToken, "true", ttl);
  }

  public boolean isTokenBlacklisted(String accessToken) {
    return Boolean.TRUE.equals(tokenRedisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + accessToken));
  }

  public void addUserToBlacklist(String userId) {
    tokenRedisTemplate.opsForValue().set(USER_BLACKLIST_PREFIX + userId, "banned");
  }

  public boolean isUserBlacklisted(String userId) {
    return Boolean.TRUE.equals(tokenRedisTemplate.hasKey(USER_BLACKLIST_PREFIX + userId));
  }

  public void removeUserFromBlacklist(String userId) {
    tokenRedisTemplate.delete(USER_BLACKLIST_PREFIX + userId);
  }
}
