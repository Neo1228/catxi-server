package com.catxi.chat.service;

import static com.catxi.chat.domain.QChatRoom.*;

import com.catxi.chat.domain.ChatParticipant;
import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.domain.ChatRoomSummary;
import com.catxi.chat.domain.KickedParticipant;
import com.catxi.chat.domain.RoomStatus;
import com.catxi.chat.dto.ChatParticipantKicked;
import com.catxi.chat.dto.ChatRoomAccessRevoked;
import com.catxi.chat.dto.ChatRoomInfoRes;
import com.catxi.chat.dto.ChatRoomPageRes;
import com.catxi.chat.dto.ChatRoomParticipantsUpdated;
import com.catxi.chat.dto.ChatRoomRes;
import com.catxi.chat.dto.ParticipantBrief;
import com.catxi.chat.dto.RoomCreateReq;
import com.catxi.chat.dto.RoomCreateRes;
import com.catxi.chat.dto.RoomDeletedEvent;
import com.catxi.chat.error.ChatParticipantErrorCode;
import com.catxi.chat.error.ChatRoomErrorCode;
import com.catxi.chat.repository.ChatMessageRepository;
import com.catxi.chat.repository.ChatParticipantRepository;
import com.catxi.chat.repository.ChatRoomRepository;
import com.catxi.chat.repository.KickedParticipantRepository;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.common.domain.Location;
import com.catxi.common.persistence.DatabaseConstraint;
import com.catxi.member.domain.Member;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatParticipantRepository chatParticipantRepository;
  private final MemberRepository memberRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ApplicationEventPublisher applicationEventPublisher;

  private final KickedParticipantRepository kickedParticipantRepository;

  private final ChatMessageService chatMessageService;
  private final MatchHistoryRecorder matchHistoryRecorder;

  @Transactional
  public RoomCreateRes createRoom(RoomCreateReq roomReq, String email) {
    if (roomReq.startPoint() == null
        || roomReq.endPoint() == null
        || roomReq.departAt() == null
        || roomReq.recruitSize() == null
        || roomReq.recruitSize() < 1
        || roomReq.recruitSize() > 4) {
      throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
    }
    if (roomReq.startPoint().equals(roomReq.endPoint())) {
      throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
    }

    Member host =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    ensureMemberIsNotInOtherRoom(host);

    ChatRoom room =
        ChatRoom.builder()
            .host(host)
            .startPoint(roomReq.startPoint())
            .endPoint(roomReq.endPoint())
            .departAt(roomReq.departAt())
            .status(RoomStatus.WAITING)
            .maxCapacity(roomReq.recruitSize())
            .build();
    chatRoomRepository.save(room);

    ChatParticipant hostPart =
        ChatParticipant.builder().chatRoom(room).member(host).isHost(true).isReady(true).build();
    saveParticipant(hostPart);

    return new RoomCreateRes(
        room.getRoomId(),
        room.getStartPoint(),
        room.getEndPoint(),
        room.getMaxCapacity(),
        room.getDepartAt(),
        room.getStatus());
  }

  @Transactional(readOnly = true)
  public ChatRoomPageRes getChatRoomPage(
      String direction, String station, String sort, Integer page, String requesterEmail) {
    if (page == null || page < 0) {
      throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
    }
    Pageable pageable = PageRequest.of(page, 10, Sort.by(sort));

    Location location =
        switch (station) {
          case "SOSA_ST" -> Location.SOSA_ST;
          case "YEOKGOK_ST" -> Location.YEOKGOK_ST;
          case "ALL" -> null;
          default -> throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
        };

    Page<ChatRoomRes> rooms =
        chatRoomRepository
            .findByLocationAndDirection(location, direction, pageable)
            .map(this::toChatRoomResponse);
    Long myRoomId = findMyChatRoomId(requesterEmail).orElse(null);
    return ChatRoomPageRes.of(rooms, myRoomId);
  }

  private ChatRoomRes toChatRoomResponse(ChatRoomSummary room) {
    return new ChatRoomRes(
        room.roomId(),
        room.hostId(),
        room.hostName(),
        room.hostNickname(),
        room.matchCount(),
        room.startPoint(),
        room.endPoint(),
        room.recruitSize(),
        room.currentSize(),
        room.status(),
        room.departAt(),
        room.createdTime());
  }

  @Transactional
  public void removeChatRoom(Long roomId, String email) {
    leaveChatRoom(roomId, email, true);
  }

  @Transactional
  public void leaveChatRoom(Long roomId, String email) {
    leaveChatRoom(roomId, email, false);
  }

  private void leaveChatRoom(Long roomId, String email, boolean hostOnly) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    ChatRoom chatRoom =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    if (hostOnly && !chatRoom.getHost().equals(member)) {
      throw new CatxiException(ChatRoomErrorCode.NOT_HOST);
    }
    ChatParticipant chatParticipant =
        chatParticipantRepository
            .findByChatRoomAndMember(chatRoom, member)
            .orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));
    if (chatParticipant.isHost()) {
      if (!chatRoom.getHost().equals(member)) {
        throw new CatxiException(ChatRoomErrorCode.NOT_HOST);
      }
      Long id = chatRoom.getRoomId();
      var hostNickname = member.getNickname();
      matchHistoryRecorder.recordForMatchedRoom(chatRoom);

      // 트랜잭션 커밋 후 Redis로 브로드캐스트되도록 이벤트 발행
      applicationEventPublisher.publishEvent(new RoomDeletedEvent(id, hostNickname));

      chatMessageRepository.deleteByChatRoomIds(List.of(id));
      chatRoomRepository.delete(chatRoom);
      return;
    }

    applicationEventPublisher.publishEvent(new ChatRoomAccessRevoked(roomId, member.getId()));
    chatParticipantRepository.delete(chatParticipant);

    sendParticipantUpdateMessage(chatRoom);

    String systemMessage = member.getNickname() + " 님이 퇴장하셨습니다.";
    chatMessageService.sendSystemMessage(roomId, systemMessage);
  }

  @Transactional
  public void joinChatRoom(Long roomId, String email) {
    ChatRoom chatRoom =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    if (kickedParticipantRepository.existsByChatRoomAndMember(chatRoom, member)) {
      throw new CatxiException(ChatParticipantErrorCode.BLOCKED_FROM_ROOM);
    }

    ensureMemberIsNotInOtherRoom(member);

    if (chatRoom.getStatus() != RoomStatus.WAITING)
      throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);

    long current = chatParticipantRepository.countByChatRoom(chatRoom);
    if (current >= chatRoom.getMaxCapacity() + 1)
      throw new CatxiException(ChatRoomErrorCode.CHATROOM_FULL);

    ChatParticipant chatParticipant =
        ChatParticipant.builder().chatRoom(chatRoom).member(member).build();

    saveParticipant(chatParticipant);

    sendParticipantUpdateMessage(chatRoom);

    chatMessageService.sendSystemMessage(roomId, member.getNickname() + " 님이 입장하셨습니다.");
  }

  @Transactional(readOnly = true)
  public Long getMyChatRoomId(String email) {
    return findMyChatRoomId(email)
        .orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public Optional<Long> findMyChatRoomId(String email) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    return chatParticipantRepository
        .findByMember(member)
        .map(participant -> participant.getChatRoom().getRoomId());
  }

  @Transactional
  public void kickUser(Long roomId, String requesterEmail, String targetEmail) {
    ChatRoom room =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    Member requester =
        memberRepository
            .findByEmail(requesterEmail)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    Member target =
        memberRepository
            .findByEmail(targetEmail)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    if (!room.getHost().equals(requester)) {
      throw new CatxiException(ChatRoomErrorCode.NOT_HOST);
    }
    if (requester.getId().equals(target.getId())) {
      throw new CatxiException(ChatParticipantErrorCode.CANNOT_KICK_SELF);
    }

    ChatParticipant participant =
        chatParticipantRepository
            .findByChatRoomAndMember(room, target)
            .orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

    log.debug(
        "[강퇴 처리] roomId: {}, requesterId: {}, targetId: {}",
        roomId,
        requester.getId(),
        target.getId());

    applicationEventPublisher.publishEvent(new ChatRoomAccessRevoked(roomId, target.getId()));
    chatParticipantRepository.delete(participant);

    KickedParticipant kicked = KickedParticipant.builder().chatRoom(room).member(target).build();
    kickedParticipantRepository.save(kicked);

    sendParticipantUpdateMessage(room);

    String msg = target.getNickname() + " 님이 강퇴되었습니다.";
    chatMessageService.sendSystemMessage(roomId, msg);

    applicationEventPublisher.publishEvent(new ChatParticipantKicked(target.getEmail()));
  }

  private void ensureMemberIsNotInOtherRoom(Member host) {
    boolean exists = chatParticipantRepository.existsByMember(host);
    if (exists) throw new CatxiException(ChatParticipantErrorCode.ALREADY_IN_ROOM);
  }

  private void saveParticipant(ChatParticipant participant) {
    try {
      chatParticipantRepository.save(participant);
    } catch (DataIntegrityViolationException exception) {
      if (DatabaseConstraint.hasName(exception, "uk_chat_participant_member_id")) {
        throw new CatxiException(ChatParticipantErrorCode.ALREADY_IN_ROOM);
      }
      throw exception;
    }
  }

  @Transactional(readOnly = true)
  public boolean isHost(Long roomId, String email) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    ChatRoom chatRoom =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    return chatRoom.getHost().equals(member);
  }

  @Transactional
  public boolean isRoomParticipant(String email, Long roomId) {
    ChatRoom chatRoom =
        chatRoomRepository
            .findByIdForShare(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    return chatParticipantRepository.existsByChatRoomAndMember(chatRoom, member);
  }

  @Transactional(readOnly = true)
  public void checkRoomEnter(Long roomId, String email) {
    ChatRoom chatRoom =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    boolean isParticipant = chatParticipantRepository.existsByChatRoomAndMember(chatRoom, member);
    if (chatRoom.getStatus() == RoomStatus.READY_LOCKED && !isParticipant) {
      throw new CatxiException(ChatRoomErrorCode.CHATROOM_READY_LOCKED);
    }
    if (!isParticipant) {
      throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
    }
  }

  @Transactional(readOnly = true)
  public ChatRoomInfoRes getChatRoomInfo(Long roomId, String email) {
    ChatRoom chatRoom =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    boolean isParticipant = isRoomParticipant(email, roomId);

    if (!isParticipant) {
      throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
    }

    List<ChatParticipantRepository.ChatParticipantSummary> participants =
        chatParticipantRepository.findSummariesByChatRoom(chatRoom);
    List<String> participantEmails =
        participants.stream()
            .map(ChatParticipantRepository.ChatParticipantSummary::getEmail)
            .toList();
    List<String> participantNicknames =
        participants.stream()
            .map(ChatParticipantRepository.ChatParticipantSummary::getNickname)
            .toList();

    return new ChatRoomInfoRes(
        (long) participantEmails.size(),
        chatRoom.getMaxCapacity(),
        chatRoom.getStatus(),
        chatRoom.getHost().getEmail(),
        chatRoom.getHost().getNickname(),
        participantEmails,
        participantNicknames,
        chatRoom.getStartPoint(),
        chatRoom.getEndPoint(),
        chatRoom.getDepartAt());
  }

  private void sendParticipantUpdateMessage(ChatRoom chatRoom) {
    List<ParticipantBrief> participants =
        chatParticipantRepository.findSummariesByChatRoom(chatRoom).stream()
            .map(
                participant ->
                    new ParticipantBrief(participant.getNickname(), participant.getEmail()))
            .toList();
    applicationEventPublisher.publishEvent(
        new ChatRoomParticipantsUpdated(chatRoom.getRoomId(), participants));
  }
}
