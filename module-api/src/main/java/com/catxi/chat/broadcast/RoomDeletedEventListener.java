package com.catxi.chat.broadcast;

import com.catxi.chat.event.RoomDeletedEvent;
import com.catxi.chat.broadcast.message.RoomEventMessage;
import com.catxi.chat.websocket.StompSubscriptionRevoker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomDeletedEventListener {

  private final @Qualifier("chatPubSub") StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final StompSubscriptionRevoker revoker;

  @EventListener
  public void closeRoomSubscriptions(RoomDeletedEvent event) {
    revoker.markRoomClosingLocally(event.roomId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
  public void restoreRoomSubscriptions(RoomDeletedEvent event) {
    revoker.cancelRoomClosingLocally(event.roomId());
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void advanceRoomRevision(RoomDeletedEvent event) {
    revoker.advanceRoomRevision(event.roomId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(RoomDeletedEvent e) {
    try {
      String message =
          e.hostNickname() == null ? "채팅방이 종료되었습니다." : e.hostNickname() + " 님이 방을 삭제했습니다.";
      String json =
          objectMapper.writeValueAsString(new RoomEventMessage(e.roomId(), "DELETED", message));
      // 방 단위 브로드캐스트
      redis.convertAndSend("roomdeleted:" + e.roomId(), json);
      revoker.finishRoomClosingIfEmpty(e.roomId());

    } catch (Exception ex) {
      log.warn("방 삭제 알림 발행 실패: roomId={}", e.roomId(), ex);
      revoker.revokeRoomLocally(e.roomId());
    } finally {
      try {
        revoker.deleteRoomAccessState(e.roomId());
      } catch (RuntimeException ex) {
        log.warn("삭제된 채팅방 권한 키 정리 실패: roomId={}", e.roomId(), ex);
      }
    }
  }
}
