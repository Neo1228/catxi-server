package com.catxi.chat.broadcast;

import com.catxi.chat.event.ChatRoomAccessRevoked;
import com.catxi.chat.websocket.StompSubscriptionRevoker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRoomAccessRevocationListener {
  private final StompSubscriptionRevoker revoker;
  private final @Qualifier("chatPubSub") StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void advanceMemberRevision(ChatRoomAccessRevoked event) {
    revoker.advanceMemberRevision(event.roomId(), event.memberId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publishRevocation(ChatRoomAccessRevoked event) {
    revoker.revokeMemberLocally(event.roomId(), event.memberId());
    try {
      String payload = objectMapper.writeValueAsString(event);
      redis.convertAndSend(ChatRedisChannels.ROOM_ACCESS_REVOKED_PREFIX + event.roomId(), payload);
    } catch (Exception exception) {
      log.error(
          "채팅방 권한 회수 전파 실패: roomId={}, memberId={}", event.roomId(), event.memberId(), exception);
    }
  }
}
