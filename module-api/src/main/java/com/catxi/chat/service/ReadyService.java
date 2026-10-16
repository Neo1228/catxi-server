package com.catxi.chat.service;

import com.catxi.chat.domain.ChatParticipant;
import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.domain.RoomStatus;
import com.catxi.chat.dto.ReadyMessageEvent;
import com.catxi.chat.dto.ReadyMessageRes;
import com.catxi.chat.dto.ReadyTimeoutRequestedEvent;
import com.catxi.chat.error.ChatParticipantErrorCode;
import com.catxi.chat.error.ChatRoomErrorCode;
import com.catxi.chat.repository.ChatParticipantRepository;
import com.catxi.chat.repository.ChatRoomRepository;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.domain.Member;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MemberRepository;
import com.catxi.contract.notification.FcmNotificationEvent;
import com.catxi.notification.fcm.error.FcmErrorCode;
import com.catxi.notification.fcm.service.FcmOutboxService;
import java.time.LocalDateTime;
import java.util.List;
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
  private final MemberRepository memberRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final ChatRoomService chatRoomService;
  private final FcmOutboxService fcmOutboxService;

  @Transactional
  public void requestReady(Long roomId, String email) {
    ChatRoom room =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    ChatParticipant participant =
        chatParticipantRepository
            .findByChatRoomAndMember(room, member)
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
    ReadyMessageRes payload =
        ReadyMessageRes.readyRequest(
            roomId, member.getId(), member.getEmail(), member.getMemberName());
    eventPublisher.publishEvent(new ReadyMessageEvent("ready:" + roomId, payload));

    sendReadyRequestNotification(room, member);

    eventPublisher.publishEvent(new ReadyTimeoutRequestedEvent(roomId));
  }

  @Transactional
  public void acceptReady(Long roomId, String email) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    ChatRoom room =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    ChatParticipant participant =
        chatParticipantRepository
            .findByChatRoomAndMember(room, member)
            .orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

    checkParticipant(room, participant);

    participant.setReady(true);

    ReadyMessageRes payload =
        ReadyMessageRes.readyAccept(
            roomId, member.getId(), member.getEmail(), member.getMemberName());
    eventPublisher.publishEvent(new ReadyMessageEvent("ready:" + roomId, payload));
  }

  @Transactional
  public void rejectReady(Long roomId, String email) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    ChatRoom room =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    ChatParticipant participant =
        chatParticipantRepository
            .findByChatRoomAndMember(room, member)
            .orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

    checkParticipant(room, participant);

    ReadyMessageRes payload =
        ReadyMessageRes.readyDeny(
            roomId, member.getId(), member.getEmail(), member.getMemberName());
    eventPublisher.publishEvent(new ReadyMessageEvent("ready:" + roomId, payload));
    chatRoomService.leaveChatRoom(roomId, email);
  }

  private void sendReadyRequestNotification(ChatRoom room, Member host) {
    try {
      log.info("Ready FCM Outbox 저장 시작: RoomId={}", room.getRoomId());

      // 방에 참여한 다른 사용자들 조회 (방장 제외)
      List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(room);

      List<Long> targetMemberIds =
          participants.stream()
              .filter(participant -> participant.getMember() != null)
              .filter(participant -> !participant.getMember().getId().equals(host.getId()))
              .map(participant -> participant.getMember().getId())
              .toList();

      if (!targetMemberIds.isEmpty()) {
        // FcmNotificationEvent 생성
        FcmNotificationEvent event =
            FcmNotificationEvent.createReadyRequest(targetMemberIds, room.getRoomId());

        fcmOutboxService.enqueue(event);

        log.info(
            "FCM 준비요청 Outbox 저장 완료 - Room ID: {}, Targets: {}",
            room.getRoomId(),
            targetMemberIds.size());
      }

    } catch (Exception e) {
      log.error("준비요청 FCM Outbox 저장 실패 - Room ID: {}, Host: {}", room.getRoomId(), host.getId(), e);
      throw new CatxiException(FcmErrorCode.FCM_OUTBOX_SAVE_FAILED);
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
}
