package com.catxi.chat.service;

import com.catxi.chat.domain.ChatMessage;
import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.domain.MessageType;
import com.catxi.chat.error.ChatMessageErrorCode;
import com.catxi.chat.error.ChatParticipantErrorCode;
import com.catxi.chat.error.ChatRoomErrorCode;
import com.catxi.chat.event.ChatMessageNotificationRequested;
import com.catxi.chat.event.ChatSystemMessageEvent;
import com.catxi.chat.repository.ChatMessageRepository;
import com.catxi.chat.repository.ChatParticipantRepository;
import com.catxi.chat.repository.ChatRoomRepository;
import com.catxi.common.api.error.CommonErrorCode;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.service.MemberService;
import com.catxi.member.service.MemberService.MemberChatProfile;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {
  private static final int LEGACY_HISTORY_LIMIT = 100;

  private final ChatRoomRepository chatRoomRepository;
  private final MemberService memberService;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatParticipantRepository chatParticipantRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public ChatMessageSendResult saveMessage(
      Long roomId, String senderEmail, String message, UUID clientMessageId) {
    if (!StringUtils.hasText(message) || message.length() > 500) {
      throw new CatxiException(CommonErrorCode.INVALID_PARAMETER);
    }

    MemberChatProfile sender = memberService.findChatProfileByEmail(senderEmail);

    // 방별 잠금으로 메시지 ID 발급과 커밋 순서를 맞춰 이력 재조회에서 늦은 커밋이 빠지지 않게 합니다.
    ChatRoom room =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    if (!chatParticipantRepository.existsByChatRoomAndMemberId(room, sender.id())) {
      log.warn("[메시지 전송 차단] 채팅방 비참여 사용자, roomId={}", roomId);
      throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
    }

    if (clientMessageId != null) {
      ChatMessage existing =
          chatMessageRepository
              .findByChatRoomAndMemberIdAndClientMessageId(room, sender.id(), clientMessageId)
              .orElse(null);
      if (existing != null) {
        if (!existing.getContent().equals(message)) {
          throw new CatxiException(ChatMessageErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        return new ChatMessageSendResult(
            existing.getId(), existing.getCreatedTime(), existing.getClientMessageId(), false);
      }
    }

    ChatMessage chatMsg =
        ChatMessage.builder()
            .chatRoom(room)
            .memberId(sender.id())
            .content(message)
            .clientMessageId(clientMessageId)
            .msgType(MessageType.CHAT)
            .build();

    ChatMessage savedMessage = chatMessageRepository.saveAndFlush(chatMsg);

    processChatFcmNotificationWithMessage(room, sender, savedMessage);
    return new ChatMessageSendResult(
        savedMessage.getId(),
        savedMessage.getCreatedTime(),
        savedMessage.getClientMessageId(),
        true);
  }

  /** FCM 알림 처리 - Outbox 패턴 적용 MySQL 트랜잭션 내에서 fcm_outbox에 저장 */
  private void processChatFcmNotificationWithMessage(
      ChatRoom room, MemberChatProfile sender, ChatMessage savedMessage) {
    log.info("FCM Outbox 저장 시작: RoomId={}, MessageId={}", room.getRoomId(), savedMessage.getId());

    for (Long recipientId :
        chatParticipantRepository.findRecipientMemberIdsByChatRoom(room, sender.id())) {
      publishChatNotificationRequested(recipientId, room.getRoomId(), savedMessage.getId());
    }

    log.info("FCM Outbox 저장 완료: RoomId={}, MessageId={}", room.getRoomId(), savedMessage.getId());
  }

  /** 채팅 알림을 Outbox에 저장 */
  private void publishChatNotificationRequested(Long targetMemberId, Long roomId, Long messageId) {
    eventPublisher.publishEvent(
        new ChatMessageNotificationRequested(targetMemberId, roomId, messageId));
  }

  @Transactional(readOnly = true)
  public List<MessageView> getChatHistory(Long roomId, String email) {

    ChatRoom room = requireParticipantRoom(roomId, email);
    List<ChatMessage> messages =
        chatMessageRepository.findByChatRoomOrderByCreatedTimeAscIdAsc(
            room, PageRequest.of(0, LEGACY_HISTORY_LIMIT + 1));
    if (messages.size() > LEGACY_HISTORY_LIMIT) {
      throw new CatxiException(ChatRoomErrorCode.CHAT_HISTORY_PAGINATION_REQUIRED);
    }

    return toResponses(room, messages);
  }

  @Transactional(readOnly = true)
  public MessagePage getChatHistoryPage(
      Long roomId,
      String email,
      LocalDateTime beforeCreatedAt,
      Long beforeMessageId,
      Long afterMessageId,
      int limit) {
    if (limit < 1
        || limit > 100
        || ((beforeCreatedAt == null) != (beforeMessageId == null))
        || (beforeMessageId != null && beforeMessageId < 1)
        || (afterMessageId != null && afterMessageId < 0)
        || (afterMessageId != null && beforeMessageId != null)) {
      throw new CatxiException(CommonErrorCode.INVALID_PARAMETER);
    }

    ChatRoom room = requireParticipantRoom(roomId, email);
    boolean forward = afterMessageId != null;
    List<ChatMessage> page =
        new ArrayList<>(
            forward
                ? chatMessageRepository.findMessagesAfterId(
                    room, afterMessageId, PageRequest.of(0, limit + 1))
                : beforeMessageId == null
                    ? chatMessageRepository.findFirstHistoryPage(room, PageRequest.of(0, limit + 1))
                    : chatMessageRepository.findHistoryBefore(
                        room, beforeCreatedAt, beforeMessageId, PageRequest.of(0, limit + 1)));
    boolean hasMore = page.size() > limit;
    if (hasMore) {
      page.remove(page.size() - 1);
    }
    if (!forward) {
      Collections.reverse(page);
    }

    List<MessageView> messages = toResponses(room, page);
    MessageView cursor = hasMore ? messages.get(forward ? messages.size() - 1 : 0) : null;
    LocalDateTime nextCreatedAt = cursor != null ? cursor.sentAt() : null;
    Long nextMessageId = cursor != null ? cursor.messageId() : null;
    return new MessagePage(messages, nextCreatedAt, nextMessageId, hasMore);
  }

  private ChatRoom requireParticipantRoom(Long roomId, String email) {
    MemberChatProfile member = memberService.findChatProfileByEmail(email);
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    if (!chatParticipantRepository.existsByChatRoomAndMemberId(room, member.id())) {
      throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
    }
    return room;
  }

  private List<MessageView> toResponses(ChatRoom room, List<ChatMessage> messages) {
    Map<Long, MemberChatProfile> profiles =
        memberService
            .findChatProfilesByIds(
                messages.stream()
                    .map(ChatMessage::getMemberId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(MemberChatProfile::id, profile -> profile));
    return messages.stream()
        .map(message -> toResponse(room, message, profiles.get(message.getMemberId())))
        .toList();
  }

  private MessageView toResponse(ChatRoom room, ChatMessage message, MemberChatProfile sender) {
    return new MessageView(
        sender != null ? sender.email() : "[SYSTEM]",
        message.getId(),
        room.getRoomId(),
        sender != null ? sender.id() : null,
        sender != null ? sender.nickname() : "[SYSTEM]",
        message.getContent(),
        message.getCreatedTime());
  }

  @Transactional
  public void sendSystemMessage(Long roomId, String content) {
    // 사용자 메시지와 같은 잠금 순서를 유지해 방 안의 시스템 메시지도 ID 순서에 포함합니다.
    ChatRoom chatRoom =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    ChatMessage systemMsg =
        ChatMessage.builder()
            .chatRoom(chatRoom)
            .memberId(null)
            .content(content)
            .msgType(MessageType.SYSTEM)
            .build();

    ChatMessage savedMessage = chatMessageRepository.save(systemMsg);

    eventPublisher.publishEvent(
        new ChatSystemMessageEvent(
            roomId, savedMessage.getId(), content, savedMessage.getCreatedTime()));
  }

  public record MessageView(
      String senderEmail,
      Long messageId,
      Long roomId,
      Long senderId,
      String senderName,
      String content,
      LocalDateTime sentAt) {}

  public record MessagePage(
      List<MessageView> messages,
      LocalDateTime nextCreatedAt,
      Long nextMessageId,
      boolean hasMore) {
    public MessagePage {
      messages = List.copyOf(messages);
    }
  }
}
