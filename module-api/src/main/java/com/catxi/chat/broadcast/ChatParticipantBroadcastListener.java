package com.catxi.chat.broadcast;

import com.catxi.chat.broadcast.message.ParticipantsUpdateMessage;
import com.catxi.chat.event.ChatParticipantKicked;
import com.catxi.chat.event.ChatRoomParticipantsUpdated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatParticipantBroadcastListener {

  @Qualifier("chatPubSub")
  private final StringRedisTemplate redis;

  private final ObjectMapper objectMapper;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ChatRoomParticipantsUpdated event) {
    try {
      String payload =
          objectMapper.writeValueAsString(
              new ParticipantsUpdateMessage(event.roomId(), event.participants()));
      redis.convertAndSend(ChatRedisChannels.PARTICIPANTS_PREFIX + event.roomId(), payload);
    } catch (Exception exception) {
      log.warn("참가자 변경 알림 발행 실패: roomId={}", event.roomId(), exception);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ChatParticipantKicked event) {
    try {
      redis.convertAndSend(ChatRedisChannels.KICK_PREFIX + event.email(), "KICKED");
    } catch (Exception exception) {
      log.warn("강퇴 알림 발행 실패", exception);
    }
  }
}
