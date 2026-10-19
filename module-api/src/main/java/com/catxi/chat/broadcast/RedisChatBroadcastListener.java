package com.catxi.chat.broadcast;

import com.catxi.chat.event.ChatRoomAccessRevoked;
import com.catxi.chat.broadcast.message.ChatMessageBroadcast;
import com.catxi.chat.broadcast.message.ParticipantsUpdateMessage;
import com.catxi.chat.broadcast.message.ReadyBroadcastMessage;
import com.catxi.chat.broadcast.message.RoomEventMessage;
import com.catxi.chat.map.broadcast.MapCoordinateBroadcast;
import com.catxi.chat.websocket.StompSubscriptionRevoker;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class RedisChatBroadcastListener implements MessageListener {
  private final SimpMessageSendingOperations messageTemplate;
  private final ObjectMapper objectMapper;
  private final StompSubscriptionRevoker subscriptionRevoker;

  public RedisChatBroadcastListener(
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
        MapCoordinateBroadcast coordinateRes =
            objectMapper.readValue(payload, MapCoordinateBroadcast.class);
        messageTemplate.convertAndSend("/topic/map/" + coordinateRes.roomId(), coordinateRes);
      } else if (channel.startsWith("ready:")) {
        ReadyBroadcastMessage readyMessage =
            objectMapper.readValue(payload, ReadyBroadcastMessage.class);
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
