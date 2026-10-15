package com.catxi.chat.websocket;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StompSubscriptionRevoker {
  private final RoomSubscriptionRegistry subscriptions;
  private final MessageChannel clientInboundChannel;

  public StompSubscriptionRevoker(
      RoomSubscriptionRegistry subscriptions,
      @Lazy @Qualifier("clientInboundChannel") MessageChannel clientInboundChannel) {
    this.subscriptions = subscriptions;
    this.clientInboundChannel = clientInboundChannel;
  }

  public void advanceMemberRevision(long roomId, long memberId) {
    subscriptions.advanceMemberRevision(roomId, memberId);
  }

  public void advanceRoomRevision(long roomId) {
    subscriptions.advanceRoomRevision(roomId);
  }

  public void deleteRoomAccessState(long roomId) {
    subscriptions.deleteRoomAccessState(roomId);
  }

  public void markRoomClosingLocally(long roomId) {
    subscriptions.markRoomClosing(roomId);
  }

  public void finishRoomClosingIfEmpty(long roomId) {
    subscriptions.finishRoomClosingIfEmpty(roomId);
  }

  public void cancelRoomClosingLocally(long roomId) {
    subscriptions.cancelRoomClosing(roomId);
  }

  public void revokeMemberLocally(long roomId, long memberId) {
    unsubscribe(subscriptions.removeMemberLocally(roomId, memberId));
  }

  public void revokeRoomLocally(long roomId) {
    unsubscribe(subscriptions.removeRoomLocally(roomId));
  }

  public void revokeSessionInRoom(String sessionId, long roomId) {
    unsubscribe(subscriptions.removeSessionFromRoom(roomId, sessionId));
  }

  public void unsubscribe(String sessionId, String subscriptionId) {
    subscriptions.remove(sessionId, subscriptionId);
    unsubscribe(sessionId, subscriptionId, null);
  }

  private void unsubscribe(List<RoomSubscriptionRegistry.RoomSubscription> revoked) {
    for (RoomSubscriptionRegistry.RoomSubscription subscription : revoked) {
      unsubscribe(subscription.sessionId(), subscription.subscriptionId(), subscription.roomId());
    }
  }

  private void unsubscribe(String sessionId, String subscriptionId, Long roomId) {
    try {
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
      accessor.setSessionId(sessionId);
      accessor.setSubscriptionId(subscriptionId);
      accessor.setLeaveMutable(true);
      Message<byte[]> message =
          MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
      if (!clientInboundChannel.send(message)) {
        log.warn("채팅 구독 서버 해제 요청이 거부되었습니다: roomId={}", roomId);
      }
    } catch (RuntimeException exception) {
      log.warn("채팅 구독 서버 해제 실패: roomId={}", roomId, exception);
    }
  }
}
