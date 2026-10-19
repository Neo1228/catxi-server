package com.catxi.chat.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRoomOutboundGuard implements ChannelInterceptor {
  private final RoomSubscriptionRegistry subscriptions;
  private final StompSubscriptionRevoker revoker;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
    String destination = accessor.getDestination();
    if (destination == null || !destination.startsWith("/topic/")) {
      return message;
    }

    Long roomId = StompChannelInterceptor.extractSubscribedRoomId(destination);
    String sessionId = accessor.getSessionId();
    String subscriptionId = accessor.getSubscriptionId();
    if (roomId == null || sessionId == null || subscriptionId == null) {
      return null;
    }

    RoomSubscriptionRegistry.RoomSubscription subscription =
        subscriptions.get(sessionId, subscriptionId);
    if (subscription == null || subscription.roomId() != roomId) {
      return null;
    }
    if (subscriptions.isRoomClosing(roomId)) {
      return isRoomDeletedDestination(destination) ? message : null;
    }

    RoomSubscriptionRegistry.Revisions current;
    try {
      current = subscriptions.currentRevisions(subscription);
      if (current.equals(subscription.revisions())) {
        return message;
      }
    } catch (RuntimeException exception) {
      log.warn(
          "채팅 구독 권한 확인 실패; 메시지 전달을 차단합니다: roomId={}, error={}",
          roomId,
          exception.getClass().getSimpleName());
      return null;
    }

    if (current.room() != subscription.revisions().room()) {
      if (isRoomDeletedDestination(destination)) {
        return message;
      }
      revoker.revokeSessionInRoom(sessionId, roomId);
      return null;
    }

    revoker.revokeSessionInRoom(sessionId, roomId);
    return null;
  }

  @Override
  public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
    if (!sent) {
      return;
    }
    SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
    String destination = accessor.getDestination();
    if (destination == null || !isRoomDeletedDestination(destination)) {
      return;
    }

    Long roomId = StompChannelInterceptor.extractSubscribedRoomId(destination);
    String sessionId = accessor.getSessionId();
    String subscriptionId = accessor.getSubscriptionId();
    if (roomId != null && sessionId != null && subscriptionId != null) {
      revoker.revokeSessionInRoom(sessionId, roomId);
    }
  }

  private boolean isRoomDeletedDestination(String destination) {
    return destination.startsWith("/topic/room/")
        && destination.endsWith("/deleted")
        && StompChannelInterceptor.extractSubscribedRoomId(destination) != null;
  }
}
