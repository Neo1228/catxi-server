package com.catxi.chat.service;

import com.catxi.chat.domain.ChatParticipant;
import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.domain.ReadyType;
import com.catxi.chat.domain.RoomStatus;
import com.catxi.chat.error.ChatParticipantErrorCode;
import com.catxi.chat.error.ChatRoomErrorCode;
import com.catxi.chat.event.ChatRoomAccessRevoked;
import com.catxi.chat.event.MatchCompletedNotificationRequested;
import com.catxi.chat.event.ReadyMessageEvent;
import com.catxi.chat.event.ReadyRequestNotificationRequested;
import com.catxi.chat.event.ReadyTimeoutRequestedEvent;
import com.catxi.chat.repository.ChatParticipantRepository;
import com.catxi.chat.repository.ChatRoomRepository;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.service.MemberService;
import com.catxi.member.service.MemberService.MemberChatProfile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadyService {
  private static final long READY_TIMEOUT_SECONDS = 20;

  private final ApplicationEventPublisher eventPublisher;
  private final ChatParticipantRepository chatParticipantRepository;
  private final MemberService memberService;
  private final ChatRoomRepository chatRoomRepository;
  private final ChatRoomService chatRoomService;
  private final ChatMessageService chatMessageService;
  private final MatchHistoryRecorder matchHistoryRecorder;

  @Transactional
  public void requestReady(Long roomId, String email) {
    ChatRoom room =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    MemberChatProfile member = memberService.findChatProfileByEmail(email);

    ChatParticipant participant =
        chatParticipantRepository
            .findByChatRoomAndMemberId(room, member.id())
            .orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

    if (!participant.isHost()) {
      throw new CatxiException(ChatRoomErrorCode.NOT_HOST);
    }

    if (!room.getStatus().equals(RoomStatus.WAITING)) {
      throw new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_WAITING);
    }

    long participantCount = chatParticipantRepository.countByChatRoom(room);
    room.lockForReady(LocalDateTime.now().plusSeconds(READY_TIMEOUT_SECONDS), participantCount);

    // 제한 시간이 지난 뒤 요청 당시와 같은 인원이 참여 중인지 확인합니다.
    eventPublisher.publishEvent(
        new ReadyMessageEvent(
            roomId, ReadyType.READY_REQUEST, member.id(), member.email(), member.memberName()));

    sendReadyRequestNotification(room, member);

    eventPublisher.publishEvent(new ReadyTimeoutRequestedEvent(roomId));
  }

  @Transactional
  public void acceptReady(Long roomId, String email) {
    MemberChatProfile member = memberService.findChatProfileByEmail(email);

    ChatRoom room =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    ChatParticipant participant =
        chatParticipantRepository
            .findByChatRoomAndMemberId(room, member.id())
            .orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

    checkParticipant(room, participant);

    participant.setReady(true);

    eventPublisher.publishEvent(
        new ReadyMessageEvent(
            roomId, ReadyType.READY_ACCEPT, member.id(), member.email(), member.memberName()));
  }

  @Transactional
  public void rejectReady(Long roomId, String email) {
    MemberChatProfile member = memberService.findChatProfileByEmail(email);

    ChatRoom room =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    ChatParticipant participant =
        chatParticipantRepository
            .findByChatRoomAndMemberId(room, member.id())
            .orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

    checkParticipant(room, participant);

    eventPublisher.publishEvent(
        new ReadyMessageEvent(
            roomId, ReadyType.READY_DENY, member.id(), member.email(), member.memberName()));
    chatRoomService.leaveChatRoom(roomId, email);
  }

  @Transactional
  public Optional<ReadyTimeoutResult> processReadyTimeout(Long roomId) {
    ChatRoom room =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    if (room.getStatus() != RoomStatus.READY_LOCKED
        || room.getReadyTimeoutAt() == null
        || room.getReadyTimeoutAt().isAfter(LocalDateTime.now())) {
      return Optional.empty();
    }

    Long requestedParticipantCount = room.getReadyParticipantCount();
    long readyParticipantCount = chatParticipantRepository.countByChatRoomAndIsReady(room, true);
    if (requestedParticipantCount == null) {
      log.warn("준비 요청 당시 인원 스냅샷이 없어 매칭을 보류합니다: roomId={}", roomId);
    }
    if (requestedParticipantCount != null
        && requestedParticipantCount > 1
        && requestedParticipantCount == readyParticipantCount) {
      room.matchedStatus(RoomStatus.MATCHED);
      chatRoomRepository.save(room);
      matchHistoryRecorder.recordMatchedRoom(room);
      // 실시간 전송이 누락돼도 매칭 내역과 푸시 작업은 상태 변경과 함께 저장합니다.
      List<Long> participantIds = chatParticipantRepository.findMemberIdsByChatRoom(room);
      eventPublisher.publishEvent(new MatchCompletedNotificationRequested(participantIds, roomId));
      chatMessageService.sendSystemMessage(roomId, "모든 참가자가 준비되어 매칭이 완료되었습니다.");
      return Optional.of(
          new ReadyTimeoutResult("MATCHED", "모든 참가자가 준비되었습니다. 매칭이 완료되었습니다"));
    }

    List<ChatParticipant> unreadyParticipants =
        chatParticipantRepository.findByChatRoom(room).stream()
            .filter(participant -> !participant.isReady())
            .toList();
    List<MemberChatProfile> removedMembers =
        memberService.findChatProfilesByIds(
            unreadyParticipants.stream().map(ChatParticipant::getMemberId).toList());
    List<String> removedNicknames =
        removedMembers.stream().map(MemberChatProfile::nickname).toList();
    unreadyParticipants.forEach(
        participant ->
            eventPublisher.publishEvent(
                new ChatRoomAccessRevoked(roomId, participant.getMemberId())));
    chatParticipantRepository.deleteAllByChatRoomAndIsReadyFalse(room);
    chatParticipantRepository.clearReadyStatusForNonHosts(room.getRoomId());
    for (String nickname : removedNicknames) {
      chatMessageService.sendSystemMessage(roomId, nickname + " 님이 퇴장하셨습니다.");
    }

    room.setStatus(RoomStatus.WAITING);
    chatRoomRepository.save(room);
    String message =
        requestedParticipantCount == null
            ? "준비 요청 당시 인원을 확인할 수 없어 대기 상태로 돌아갑니다. 다시 요청해 주세요."
            : "일부 참가자가 준비하지 않아 대기 상태로 돌아갑니다.";
    return Optional.of(new ReadyTimeoutResult("RETURN_WAITING", message));
  }

  private void sendReadyRequestNotification(ChatRoom room, MemberChatProfile host) {
    try {
      log.info("Ready FCM Outbox 저장 시작: RoomId={}", room.getRoomId());

      // 방에 참여한 다른 사용자들 조회 (방장 제외)
      List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(room);

      List<Long> targetMemberIds =
          participants.stream()
              .map(ChatParticipant::getMemberId)
              .filter(memberId -> !memberId.equals(host.id()))
              .toList();

      if (!targetMemberIds.isEmpty()) {
        eventPublisher.publishEvent(
            new ReadyRequestNotificationRequested(targetMemberIds, room.getRoomId()));

        log.info(
            "FCM 준비요청 Outbox 저장 완료 - Room ID: {}, Targets: {}",
            room.getRoomId(),
            targetMemberIds.size());
      }

    } catch (RuntimeException e) {
      log.error("준비 요청 알림 요청 처리 실패: roomId={}, hostId={}", room.getRoomId(), host.id(), e);
      throw e;
    }
  }

  private void checkParticipant(ChatRoom room, ChatParticipant participant) {
    if (!room.getStatus().equals(RoomStatus.READY_LOCKED)) {
      throw new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_READY_LOCKED);
    }
    if (participant.isHost()) {
      throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
    }

    if (participant.isReady()) {
      throw new CatxiException(ChatParticipantErrorCode.ALREADY_READY);
    }
  }

  public record ReadyTimeoutResult(String eventType, String eventContent) {}
}
