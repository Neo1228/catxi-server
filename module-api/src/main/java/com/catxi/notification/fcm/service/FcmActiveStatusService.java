package com.catxi.notification.fcm.service;

import com.catxi.contract.chat.ChatPresenceKeys;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.domain.Member;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MemberRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmActiveStatusService {

  private static final String MEMBER_CACHE_KEY_FORMAT = "member:email:%s";
  private static final int MEMBER_CACHE_TTL_MINUTES = 30;
  private static final String INSTANCE_ID = UUID.randomUUID().toString();

  private static final DefaultRedisScript<Long> ACTIVATE_SESSION_SCRIPT =
      new DefaultRedisScript<>(
          """
          local now = tonumber(ARGV[1])
          redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
          redis.call('ZADD', KEYS[1], ARGV[2], ARGV[4])
          redis.call('PEXPIRE', KEYS[1], ARGV[3])
          redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[3])
          return redis.call('ZCARD', KEYS[1])
          """,
          Long.class);

  private static final DefaultRedisScript<Long> DEACTIVATE_SESSION_SCRIPT =
      new DefaultRedisScript<>(
          """
          local now = tonumber(ARGV[1])
          redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
          redis.call('ZREM', KEYS[1], ARGV[2])
          local latest = redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES')
          if #latest == 0 then
            redis.call('DEL', KEYS[1], KEYS[2])
            return 0
          end
          local ttl = math.max(1, tonumber(latest[2]) - now)
          redis.call('PEXPIRE', KEYS[1], ttl)
          redis.call('SET', KEYS[2], ARGV[1], 'PX', ttl)
          return redis.call('ZCARD', KEYS[1])
          """,
          Long.class);

  private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
  private final MemberRepository memberRepository;

  /** 기존 API 계약을 유지하면서 수동 활성 상태를 별도 lease로 관리합니다. */
  public void updateUserActiveStatus(String email, Long roomId, boolean isActive) {
    try {
      Long memberId = getMemberIdFromCacheOrDb(email);
      updatePresence(memberId, roomId, "manual:" + memberId, isActive);
    } catch (Exception e) {
      log.warn(
          "채팅방 활성 상태를 갱신하지 못했습니다: roomId={}, active={}, errorType={}",
          roomId,
          isActive,
          e.getClass().getSimpleName());
    }
  }

  /** 세션 활성 상태를 등록하고 이후 갱신에 사용할 회원 ID를 반환합니다. */
  public Long updateSessionActiveStatus(
      String email, Long roomId, String sessionId, boolean isActive) {
    try {
      Long memberId = getMemberIdFromCacheOrDb(email);
      updateSessionActiveStatus(memberId, roomId, sessionId, isActive);
      return memberId;
    } catch (Exception e) {
      log.warn(
          "채팅 세션 활성 상태를 갱신하지 못했습니다: roomId={}, active={}, errorType={}",
          roomId,
          isActive,
          e.getClass().getSimpleName());
      return null;
    }
  }

  public void updateSessionActiveStatus(
      Long memberId, Long roomId, String sessionId, boolean isActive) {
    if (memberId == null || sessionId == null) {
      return;
    }
    try {
      updatePresence(memberId, roomId, INSTANCE_ID + ":" + sessionId, isActive);
    } catch (Exception e) {
      log.warn(
          "채팅 세션 활성 상태를 갱신하지 못했습니다: roomId={}, active={}, errorType={}",
          roomId,
          isActive,
          e.getClass().getSimpleName());
    }
  }

  public boolean isUserActiveInRoom(Long memberId, Long roomId) {
    try {
      return Boolean.TRUE.equals(
          redisTemplate.hasKey(
              ChatPresenceKeys.activeMarker(roomId.toString(), memberId.toString())));
    } catch (Exception e) {
      log.warn(
          "채팅방 활성 상태를 확인하지 못했습니다: roomId={}, errorType={}", roomId, e.getClass().getSimpleName());
      return false;
    }
  }

  private void updatePresence(Long memberId, Long roomId, String presenceId, boolean isActive) {
    String room = roomId.toString();
    String member = memberId.toString();
    String sessionKey = ChatPresenceKeys.sessionRegistry(room, member);
    String markerKey = ChatPresenceKeys.activeMarker(room, member);
    long now = System.currentTimeMillis();
    long leaseMillis = ChatPresenceKeys.LEASE.toMillis();

    if (isActive) {
      redisTemplate.execute(
          ACTIVATE_SESSION_SCRIPT,
          List.of(sessionKey, markerKey),
          Long.toString(now),
          Long.toString(now + leaseMillis),
          Long.toString(leaseMillis),
          presenceId);
    } else {
      redisTemplate.execute(
          DEACTIVATE_SESSION_SCRIPT,
          List.of(sessionKey, markerKey),
          Long.toString(now),
          presenceId);
    }
  }

  private Long getMemberIdFromCacheOrDb(String email) {
    String cacheKey = MEMBER_CACHE_KEY_FORMAT.formatted(email);
    try {
      String cachedMemberId = redisTemplate.opsForValue().get(cacheKey);
      if (cachedMemberId != null) {
        return Long.parseLong(cachedMemberId);
      }
    } catch (NumberFormatException e) {
      log.warn("캐시된 회원 ID가 올바르지 않아 데이터베이스에서 다시 조회합니다");
      redisTemplate.delete(cacheKey);
    }

    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    redisTemplate
        .opsForValue()
        .set(cacheKey, member.getId().toString(), MEMBER_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    return member.getId();
  }
}
