package com.catxi.chat.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomSubscriptionRegistry {
  private static final String KEY_PREFIX = "catxi:chat:access:";
  private static final String ROOM_EPOCH = "epoch";
  private static final String ROOM_REVISION = "room";
  private static final String MEMBER_PREFIX = "member:";

  private final @Qualifier("chatPubSub") StringRedisTemplate redis;
  private final ConcurrentMap<SubscriptionKey, RoomSubscription> subscriptions =
      new ConcurrentHashMap<>();
  private final Set<Long> closingRooms = ConcurrentHashMap.newKeySet();

  public Revisions captureRevisions(long roomId, long memberId) {
    String key = revisionKey(roomId);
    // 키를 다시 만들 때마다 UUID를 바꿔 오래된 구독의 권한 카운터가 재사용되지 않도록 합니다.
    redis.opsForHash().putIfAbsent(key, ROOM_EPOCH, UUID.randomUUID().toString());
    List<?> values =
        redis.opsForHash().multiGet(key, List.of(ROOM_EPOCH, ROOM_REVISION, memberField(memberId)));
    String roomEpoch = readValue(values, 0);
    if (roomEpoch == null) {
      throw new IllegalStateException("채팅방 권한 세대를 초기화하지 못했습니다");
    }
    return new Revisions(roomEpoch, readRevision(values, 1), readRevision(values, 2));
  }

  public void register(
      String sessionId, String subscriptionId, long roomId, long memberId, Revisions revisions) {
    if (sessionId == null || subscriptionId == null || subscriptionId.isBlank()) {
      throw new IllegalArgumentException("STOMP 세션과 구독 ID가 필요합니다");
    }
    if (closingRooms.contains(roomId)) {
      throw new IllegalStateException("삭제 중인 채팅방에는 구독할 수 없습니다");
    }
    SubscriptionKey key = new SubscriptionKey(sessionId, subscriptionId);
    RoomSubscription subscription =
        new RoomSubscription(sessionId, subscriptionId, roomId, memberId, revisions);
    subscriptions.put(key, subscription);
    if (closingRooms.contains(roomId) && subscriptions.remove(key, subscription)) {
      throw new IllegalStateException("삭제 중인 채팅방에는 구독할 수 없습니다");
    }
  }

  public RoomSubscription get(String sessionId, String subscriptionId) {
    if (sessionId == null || subscriptionId == null) {
      return null;
    }
    return subscriptions.get(new SubscriptionKey(sessionId, subscriptionId));
  }

  public Revisions currentRevisions(RoomSubscription subscription) {
    List<?> values =
        redis
            .opsForHash()
            .multiGet(
                revisionKey(subscription.roomId()),
                List.of(ROOM_EPOCH, ROOM_REVISION, memberField(subscription.memberId())));
    return new Revisions(readValue(values, 0), readRevision(values, 1), readRevision(values, 2));
  }

  public void remove(String sessionId, String subscriptionId) {
    if (sessionId != null && subscriptionId != null) {
      RoomSubscription removed =
          subscriptions.remove(new SubscriptionKey(sessionId, subscriptionId));
      if (removed != null) {
        clearClosingRoomIfEmpty(removed.roomId());
      }
    }
  }

  public void removeSession(String sessionId) {
    if (sessionId != null) {
      List<Long> roomIds =
          subscriptions.values().stream()
              .filter(subscription -> subscription.sessionId().equals(sessionId))
              .map(RoomSubscription::roomId)
              .distinct()
              .toList();
      subscriptions.keySet().removeIf(key -> key.sessionId().equals(sessionId));
      roomIds.forEach(this::clearClosingRoomIfEmpty);
    }
  }

  public void advanceMemberRevision(long roomId, long memberId) {
    redis.opsForHash().increment(revisionKey(roomId), memberField(memberId), 1L);
  }

  public void advanceRoomRevision(long roomId) {
    redis.opsForHash().increment(revisionKey(roomId), ROOM_REVISION, 1L);
  }

  public void deleteRoomAccessState(long roomId) {
    redis.delete(revisionKey(roomId));
  }

  public void markRoomClosing(long roomId) {
    closingRooms.add(roomId);
  }

  public void finishRoomClosingIfEmpty(long roomId) {
    clearClosingRoomIfEmpty(roomId);
  }

  public void cancelRoomClosing(long roomId) {
    closingRooms.remove(roomId);
  }

  public boolean isRoomClosing(long roomId) {
    return closingRooms.contains(roomId);
  }

  public List<RoomSubscription> removeMemberLocally(long roomId, long memberId) {
    return removeMatching(
        subscription -> subscription.roomId() == roomId && subscription.memberId() == memberId);
  }

  public List<RoomSubscription> removeRoomLocally(long roomId) {
    List<RoomSubscription> removed =
        removeMatching(subscription -> subscription.roomId() == roomId);
    closingRooms.remove(roomId);
    return removed;
  }

  public List<RoomSubscription> removeSessionFromRoom(long roomId, String sessionId) {
    List<RoomSubscription> removed =
        removeMatching(
            subscription ->
                subscription.roomId() == roomId && subscription.sessionId().equals(sessionId));
    clearClosingRoomIfEmpty(roomId);
    return removed;
  }

  private List<RoomSubscription> removeMatching(Predicate<RoomSubscription> predicate) {
    List<RoomSubscription> removed = new ArrayList<>();
    subscriptions.forEach(
        (key, subscription) -> {
          if (predicate.test(subscription) && subscriptions.remove(key, subscription)) {
            removed.add(subscription);
          }
        });
    removed.stream()
        .map(RoomSubscription::roomId)
        .distinct()
        .forEach(this::clearClosingRoomIfEmpty);
    return removed;
  }

  private void clearClosingRoomIfEmpty(long roomId) {
    if (closingRooms.contains(roomId)
        && subscriptions.values().stream()
            .noneMatch(subscription -> subscription.roomId() == roomId)) {
      closingRooms.remove(roomId);
    }
  }

  private long readRevision(List<?> values, int index) {
    if (values == null || index >= values.size() || values.get(index) == null) {
      return 0;
    }
    return Long.parseLong(values.get(index).toString());
  }

  private String readValue(List<?> values, int index) {
    return values == null || index >= values.size() || values.get(index) == null
        ? null
        : values.get(index).toString();
  }

  private String revisionKey(long roomId) {
    return KEY_PREFIX + roomId;
  }

  private String memberField(long memberId) {
    return MEMBER_PREFIX + memberId;
  }

  public record Revisions(String roomEpoch, long room, long member) {}

  public record RoomSubscription(
      String sessionId, String subscriptionId, long roomId, long memberId, Revisions revisions) {}

  private record SubscriptionKey(String sessionId, String subscriptionId) {}
}
