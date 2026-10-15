package com.catxi.chat.realtime;

import com.catxi.chat.dto.ChatCoordinateBroadcast;
import com.catxi.chat.dto.ChatMessageBroadcast;
import com.catxi.chat.dto.ChatRoomAccessRevoked;
import com.catxi.chat.dto.ParticipantsUpdateMessage;
import com.catxi.chat.dto.ReadyMessageRes;
import com.catxi.chat.dto.RoomEventMessage;
import com.catxi.chat.websocket.StompSubscriptionRevoker;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class RedisPubSubService implements MessageListener {
  private final SimpMessageSendingOperations messageTemplate;
  private final ObjectMapper objectMapper;
  private final StompSubscriptionRevoker subscriptionRevoker;

  public RedisPubSubService(
      SimpMessageSendingOperations messageTemplate,
      ObjectMapper objectMapper,
      StompSubscriptionRevoker subscriptionRevoker) {
    this.messageTemplate = messageTemplate;
    this.objectMapper = objectMapper;
    this.subscriptionRevoker = subscriptionRevoker;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);
    log.debug("Redis Pub/Sub 메시지를 수신했습니다");

    try {
      if ("chat".equals(channel)) {
        ChatMessageBroadcast chatMessageDto =
            objectMapper.readValue(payload, ChatMessageBroadcast.class);
        messageTemplate.convertAndSend("/topic/" + chatMessageDto.roomId(), chatMessageDto);

      } else if ("map".equals(channel)) {
        ChatCoordinateBroadcast coordinateRes =
            objectMapper.readValue(payload, ChatCoordinateBroadcast.class);
        messageTemplate.convertAndSend("/topic/map/" + coordinateRes.roomId(), coordinateRes);
      } else if (channel.startsWith("ready:")) {
        ReadyMessageRes readyMessage = objectMapper.readValue(payload, ReadyMessageRes.class);
        messageTemplate.convertAndSend("/topic/ready/" + readyMessage.roomId(), readyMessage);
      } else if (channel.startsWith("participants:")) {
        ParticipantsUpdateMessage update =
            objectMapper.readValue(payload, ParticipantsUpdateMessage.class);
        messageTemplate.convertAndSend("/topic/room/" + update.roomId() + "/participants", update);
      } else if (channel.startsWith("kick:")) {
        String email = channel.split(":", 2)[1];
        messageTemplate.convertAndSendToUser(email, "/queue/kick", "KICKED");
      } else if (channel.startsWith("roomaccessrevoked:")) {
        ChatRoomAccessRevoked event = objectMapper.readValue(payload, ChatRoomAccessRevoked.class);
        subscriptionRevoker.revokeMemberLocally(event.roomId(), event.memberId());
      } else if (channel.startsWith("roomdeleted:")) {
        RoomEventMessage evt = objectMapper.readValue(payload, RoomEventMessage.class);
        subscriptionRevoker.markRoomClosingLocally(evt.roomId());
        messageTemplate.convertAndSend("/topic/room/" + evt.roomId() + "/deleted", evt);
        subscriptionRevoker.finishRoomClosingIfEmpty(evt.roomId());
      } else if (channel.startsWith("readyresult:")) {
        RoomEventMessage evt = objectMapper.readValue(payload, RoomEventMessage.class);
        messageTemplate.convertAndSend("/topic/ready/" + evt.roomId() + "/result", evt);
      }
    } catch (JacksonException e) {
      log.warn("Redis Pub/Sub 이벤트 JSON을 읽지 못했습니다: {}", e.getClass().getSimpleName());
    }
  }
}
