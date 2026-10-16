package com.catxi.chat.service;

import com.catxi.chat.domain.ChatMessage;
import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.domain.MessageType;
import com.catxi.chat.dto.ChatMessagePageRes;
import com.catxi.chat.dto.ChatMessageRes;
import com.catxi.chat.dto.ChatMessageSendReq;
import com.catxi.chat.dto.ChatMessageSendResult;
import com.catxi.chat.dto.ChatSystemMessageEvent;
import com.catxi.chat.error.ChatMessageErrorCode;
import com.catxi.chat.error.ChatParticipantErrorCode;
import com.catxi.chat.error.ChatRoomErrorCode;
import com.catxi.chat.repository.ChatMessageRepository;
import com.catxi.chat.repository.ChatParticipantRepository;
import com.catxi.chat.repository.ChatRoomRepository;
import com.catxi.common.api.error.CommonErrorCode;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.domain.Member;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MemberRepository;
import com.catxi.contract.notification.FcmNotificationEvent;
import com.catxi.notification.fcm.service.FcmOutboxService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
  private final MemberRepository memberRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatParticipantRepository chatParticipantRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final FcmOutboxService fcmOutboxService;

  @Transactional
  public ChatMessageSendResult saveMessage(
      Long roomId, String senderEmail, ChatMessageSendReq req) {
    if (req == null || !StringUtils.hasText(req.message()) || req.message().length() > 500) {
      throw new CatxiException(CommonErrorCode.INVALID_PARAMETER);
    }

    // 방별 잠금으로 메시지 ID 발급과 커밋 순서를 맞춰 이력 재조회에서 늦은 커밋이 빠지지 않게 합니다.
    ChatRoom room =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    Member sender =
        memberRepository
            .findByEmail(senderEmail)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    if (!chatParticipantRepository.existsByChatRoomAndMember(room, sender)) {
      log.warn("[메시지 전송 차단] 채팅방 비참여 사용자, roomId={}", roomId);
      throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
    }

    if (req.clientMessageId() != null) {
      ChatMessage existing =
          chatMessageRepository
              .findByChatRoomAndMemberAndClientMessageId(room, sender, req.clientMessageId())
              .orElse(null);
      if (existing != null) {
        if (!existing.getContent().equals(req.message())) {
          throw new CatxiException(ChatMessageErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        return new ChatMessageSendResult(
            existing.getId(), existing.getCreatedTime(), existing.getClientMessageId(), false);
      }
    }

    ChatMessage chatMsg =
        ChatMessage.builder()
            .chatRoom(room)
            .member(sender)
            .content(req.message())
            .clientMessageId(req.clientMessageId())
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
      ChatRoom room, Member sender, ChatMessage savedMessage) {
    log.info("FCM Outbox 저장 시작: RoomId={}, MessageId={}", room.getRoomId(), savedMessage.getId());

    for (Long recipientId :
        chatParticipantRepository.findRecipientMemberIdsByChatRoom(room, sender.getId())) {
      saveChatNotificationToOutbox(recipientId, room.getRoomId(), savedMessage.getId());
    }

    log.info("FCM Outbox 저장 완료: RoomId={}, MessageId={}", room.getRoomId(), savedMessage.getId());
  }

  /** 채팅 알림을 Outbox에 저장 */
  private void saveChatNotificationToOutbox(Long targetMemberId, Long roomId, Long messageId) {
    FcmNotificationEvent event =
        FcmNotificationEvent.createChatMessage(targetMemberId, roomId, messageId);
    fcmOutboxService.enqueue(event);
    log.debug("FCM Outbox 저장: EventId={}, Target={}", event.eventId(), targetMemberId);
  }

  @Transactional(readOnly = true)
  public List<ChatMessageRes> getChatHistory(Long roomId, String email) {

    ChatRoom room = requireParticipantRoom(roomId, email);
    List<ChatMessage> messages =
        chatMessageRepository.findByChatRoomOrderByCreatedTimeAscIdAsc(
            room, PageRequest.of(0, LEGACY_HISTORY_LIMIT + 1));
    if (messages.size() > LEGACY_HISTORY_LIMIT) {
      throw new CatxiException(ChatRoomErrorCode.CHAT_HISTORY_PAGINATION_REQUIRED);
    }

    return messages.stream().map(message -> toResponse(room, message)).toList();
  }

  @Transactional(readOnly = true)
  public ChatMessagePageRes getChatHistoryPage(
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

    List<ChatMessageRes> messages =
        page.stream().map(message -> toResponse(room, message)).toList();
    ChatMessageRes cursor = hasMore ? messages.get(forward ? messages.size() - 1 : 0) : null;
    LocalDateTime nextCreatedAt = cursor != null ? cursor.sentAt() : null;
    Long nextMessageId = cursor != null ? cursor.messageId() : null;
    return new ChatMessagePageRes(messages, nextCreatedAt, nextMessageId, hasMore);
  }

  private ChatRoom requireParticipantRoom(Long roomId, String email) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    if (!chatParticipantRepository.existsByChatRoomAndMember(room, member)) {
      throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
    }
    return room;
  }

  private ChatMessageRes toResponse(ChatRoom room, ChatMessage message) {
    Member sender = message.getMember();
    return new ChatMessageRes(
        sender != null ? sender.getEmail() : "[SYSTEM]",
        message.getId(),
        room.getRoomId(),
        sender != null ? sender.getId() : null,
        sender != null ? sender.getNickname() : "[SYSTEM]",
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
            .member(null)
            .content(content)
            .msgType(MessageType.SYSTEM)
            .build();

    ChatMessage savedMessage = chatMessageRepository.save(systemMsg);

    eventPublisher.publishEvent(
        new ChatSystemMessageEvent(
            roomId, savedMessage.getId(), content, savedMessage.getCreatedTime()));
  }
}
