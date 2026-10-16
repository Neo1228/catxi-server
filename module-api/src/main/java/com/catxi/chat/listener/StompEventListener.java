package com.catxi.chat.listener;

import com.catxi.chat.realtime.ChatPresenceService;
import com.catxi.chat.websocket.RoomSubscriptionRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompEventListener {
  private final Set<String> sessions = ConcurrentHashMap.newKeySet();
  private final Map<String, SessionPresence> sessionPresence = new ConcurrentHashMap<>();
  private final ChatPresenceService chatPresenceService;
  private final RoomSubscriptionRegistry roomSubscriptionRegistry;

  @EventListener
  public void connectHandle(SessionConnectEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    String sessionId = accessor.getSessionId();
    if (sessionId == null) {
      return;
    }

    sessions.add(sessionId);
    SessionPresence presence =
        sessionPresence.computeIfAbsent(sessionId, ignored -> new SessionPresence());
    synchronized (presence) {
      presence.setEmail(principalName(accessor));
    }
    log.debug("WebSocket session connected; active sessions={}", sessions.size());
  }

  @EventListener
  public void disconnectHandle(SessionDisconnectEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    String sessionId = accessor.getSessionId();
    if (sessionId == null) {
      return;
    }

    sessions.remove(sessionId);
    SessionPresence presence = sessionPresence.remove(sessionId);
    if (presence != null) {
      synchronized (presence) {
        for (Long roomId : presence.activeRooms()) {
          removePresence(presence, roomId, sessionId);
        }
        presence.clear();
      }
    }
    roomSubscriptionRegistry.removeSession(sessionId);
    log.debug("WebSocket session disconnected; active sessions={}", sessions.size());
  }

  @EventListener
  public void subscribeHandle(SessionSubscribeEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    String sessionId = accessor.getSessionId();
    String destination = accessor.getDestination();
    if (sessionId == null
        || !sessions.contains(sessionId)
        || destination == null
        || !isChatRoomDestination(destination)) {
      return;
    }

    Long roomId = roomIdFrom(destination);
    String subscriptionId = accessor.getSubscriptionId();
    if (roomId == null || subscriptionId == null) {
      return;
    }

    SessionPresence presence =
        sessionPresence.computeIfAbsent(sessionId, ignored -> new SessionPresence());
    synchronized (presence) {
      if (!sessions.contains(sessionId)) {
        sessionPresence.remove(sessionId, presence);
        return;
      }
      presence.setEmail(principalName(accessor));
      if (!presence.addSubscription(subscriptionId, roomId)) {
        return;
      }
      addPresence(presence, roomId, sessionId);
      log.debug("Chat room subscription became active: roomId={}", roomId);
    }
  }

  @EventListener
  public void unsubscribeHandle(SessionUnsubscribeEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    String sessionId = accessor.getSessionId();
    String subscriptionId = accessor.getSubscriptionId();
    if (sessionId == null || subscriptionId == null) {
      return;
    }

    SessionPresence presence = sessionPresence.get(sessionId);
    if (presence == null) {
      return;
    }
    synchronized (presence) {
      if (!sessions.contains(sessionId)) {
        return;
      }
      Long roomId = presence.removeSubscription(subscriptionId);
      if (roomId != null) {
        removePresence(presence, roomId, sessionId);
        log.debug("Chat room subscription became inactive: roomId={}", roomId);
      }
    }
  }

  @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
  public void refreshActiveSessions() {
    for (Map.Entry<String, SessionPresence> entry : sessionPresence.entrySet()) {
      String sessionId = entry.getKey();
      SessionPresence presence = entry.getValue();
      synchronized (presence) {
        if (!sessions.contains(sessionId) || presence.email() == null) {
          continue;
        }
        for (Long roomId : presence.activeRooms()) {
          addPresence(presence, roomId, sessionId);
        }
      }
    }
  }

  private void addPresence(SessionPresence presence, Long roomId, String sessionId) {
    Long memberId = presence.memberId();
    if (memberId == null) {
      presence.setMemberId(
          chatPresenceService.updateSessionActiveStatus(presence.email(), roomId, sessionId, true));
    } else {
      chatPresenceService.updateSessionActiveStatus(memberId, roomId, sessionId, true);
    }
  }

  private void removePresence(SessionPresence presence, Long roomId, String sessionId) {
    if (presence.email() == null && presence.memberId() == null) {
      return;
    }
    Long memberId = presence.memberId();
    if (memberId == null) {
      chatPresenceService.updateSessionActiveStatus(presence.email(), roomId, sessionId, false);
    } else {
      chatPresenceService.updateSessionActiveStatus(memberId, roomId, sessionId, false);
    }
  }

  private static String principalName(StompHeaderAccessor accessor) {
    return accessor.getUser() == null ? null : accessor.getUser().getName();
  }

  private static boolean isChatRoomDestination(String destination) {
    return destination.matches("/topic/(chat/)?[0-9]+");
  }

  private static Long roomIdFrom(String destination) {
    try {
      return Long.parseLong(destination.substring(destination.lastIndexOf('/') + 1));
    } catch (NumberFormatException e) {
      log.warn("채팅방 주소에서 방 ID를 읽지 못했습니다: destination={}", destination);
      return null;
    }
  }

  private static final class SessionPresence {
    private final Map<String, Long> subscriptions = new HashMap<>();
    private String email;
    private Long memberId;

    private void setEmail(String email) {
      if (this.email == null && email != null) {
        this.email = email;
      }
    }

    private String email() {
      return email;
    }

    private Long memberId() {
      return memberId;
    }

    private void setMemberId(Long memberId) {
      if (this.memberId == null && memberId != null) {
        this.memberId = memberId;
      }
    }

    private boolean addSubscription(String subscriptionId, Long roomId) {
      if (subscriptions.containsKey(subscriptionId)) {
        return false;
      }
      boolean roomAlreadyActive = subscriptions.containsValue(roomId);
      subscriptions.put(subscriptionId, roomId);
      return !roomAlreadyActive;
    }

    private Long removeSubscription(String subscriptionId) {
      Long roomId = subscriptions.remove(subscriptionId);
      return roomId != null && !subscriptions.containsValue(roomId) ? roomId : null;
    }

    private Set<Long> activeRooms() {
      return new HashSet<>(subscriptions.values());
    }

    private void clear() {
      subscriptions.clear();
    }
  }
}
