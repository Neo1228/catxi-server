package com.catxi.chat.service;

import static com.catxi.chat.domain.QChatRoom.*;

import com.catxi.chat.domain.ChatParticipant;
import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.domain.KickedParticipant;
import com.catxi.chat.domain.RoomStatus;
import com.catxi.chat.dto.ChatParticipantSummary;
import com.catxi.chat.error.ChatParticipantErrorCode;
import com.catxi.chat.error.ChatRoomErrorCode;
import com.catxi.chat.event.ChatParticipantKicked;
import com.catxi.chat.event.ChatRoomAccessRevoked;
import com.catxi.chat.event.ChatRoomParticipantsUpdated;
import com.catxi.chat.event.RoomDeletedEvent;
import com.catxi.chat.repository.ChatMessageRepository;
import com.catxi.chat.repository.ChatParticipantRepository;
import com.catxi.chat.repository.ChatRoomRepository;
import com.catxi.chat.repository.ChatRoomSummary;
import com.catxi.chat.repository.KickedParticipantRepository;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.common.domain.MeetingPoint;
import com.catxi.common.persistence.DatabaseConstraint;
import com.catxi.member.service.MemberService;
import com.catxi.member.service.MemberService.MemberChatProfile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

  private static final Set<String> ROOM_SORT_FIELDS = Set.of("departAt", "createdTime");
  private static final Set<String> ROOM_DIRECTIONS = Set.of("TO_SCHOOL", "FROM_SCHOOL");

  private final ChatRoomRepository chatRoomRepository;
  private final ChatParticipantRepository chatParticipantRepository;
  private final MemberService memberService;
  private final ChatMessageRepository chatMessageRepository;
  private final ApplicationEventPublisher applicationEventPublisher;

  private final KickedParticipantRepository kickedParticipantRepository;

  private final ChatMessageService chatMessageService;
  private final MatchHistoryRecorder matchHistoryRecorder;

  @Transactional
  public CreatedRoom createRoom(
      MeetingPoint startPoint,
      MeetingPoint endPoint,
      LocalDateTime departAt,
      Long recruitSize,
      String email) {
    if (startPoint == null
        || endPoint == null
        || departAt == null
        || recruitSize == null
        || recruitSize < 1
        || recruitSize > 4) {
      throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
    }
    if (startPoint.equals(endPoint)) {
      throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
    }

    MemberChatProfile host = memberService.findChatProfileByEmail(email);

    ensureMemberIsNotInOtherRoom(host);

    ChatRoom room =
        ChatRoom.builder()
            .hostId(host.id())
            .startPoint(startPoint)
            .endPoint(endPoint)
            .departAt(departAt)
            .status(RoomStatus.WAITING)
            .maxCapacity(recruitSize)
            .build();
    chatRoomRepository.save(room);

    ChatParticipant hostPart =
        ChatParticipant.builder()
            .chatRoom(room)
            .memberId(host.id())
            .isHost(true)
            .isReady(true)
            .build();
    saveParticipant(hostPart);

    return new CreatedRoom(
        room.getRoomId(),
        room.getStartPoint(),
        room.getEndPoint(),
        room.getMaxCapacity(),
        room.getDepartAt(),
        room.getStatus());
  }

  @Transactional(readOnly = true)
  public RoomPage getChatRoomPage(
      String direction, String station, String sort, Integer page, String requesterEmail) {
    if (page == null
        || page < 0
        || sort == null
        || !ROOM_SORT_FIELDS.contains(sort)
        || direction == null
        || !ROOM_DIRECTIONS.contains(direction)) {
      throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
    }
    Pageable pageable = PageRequest.of(page, 10, Sort.by(sort));

    MeetingPoint meetingPoint =
        switch (station) {
          case "SOSA_ST" -> MeetingPoint.SOSA_ST;
          case "YEOKGOK_ST" -> MeetingPoint.YEOKGOK_ST;
          case "ALL" -> null;
          default -> throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
        };

    Page<ChatRoomSummary> summaries =
        chatRoomRepository.findByMeetingPointAndDirection(meetingPoint, direction, pageable);
    Map<Long, MemberChatProfile> hostProfiles =
        memberService
            .findChatProfilesByIds(
                summaries.getContent().stream().map(ChatRoomSummary::hostId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(MemberChatProfile::id, profile -> profile));
    Page<RoomListItem> rooms =
        summaries.map(summary -> toRoomListItem(summary, hostProfiles.get(summary.hostId())));
    Long myRoomId = findMyChatRoomId(requesterEmail).orElse(null);
    return new RoomPage(
        myRoomId,
        rooms.getContent(),
        rooms.getTotalPages(),
        rooms.getTotalElements(),
        rooms.getNumber(),
        rooms.getSize(),
        rooms.hasNext());
  }

  private RoomListItem toRoomListItem(ChatRoomSummary room, MemberChatProfile host) {
    return new RoomListItem(
        room.roomId(),
        room.hostId(),
        host.memberName(),
        host.nickname(),
        host.matchCount(),
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
    MemberChatProfile member = memberService.findChatProfileByEmail(email);
    ChatRoom chatRoom =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    if (hostOnly && !chatRoom.getHostId().equals(member.id())) {
      throw new CatxiException(ChatRoomErrorCode.NOT_HOST);
    }
    ChatParticipant chatParticipant =
        chatParticipantRepository
            .findByChatRoomAndMemberId(chatRoom, member.id())
            .orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));
    if (chatParticipant.isHost()) {
      if (!chatRoom.getHostId().equals(member.id())) {
        throw new CatxiException(ChatRoomErrorCode.NOT_HOST);
      }
      Long id = chatRoom.getRoomId();
      var hostNickname = member.nickname();
      matchHistoryRecorder.recordMatchedRoom(chatRoom);

      // 트랜잭션 커밋 후 Redis로 브로드캐스트되도록 이벤트 발행
      applicationEventPublisher.publishEvent(new RoomDeletedEvent(id, hostNickname));

      chatMessageRepository.deleteByChatRoomIds(List.of(id));
      chatRoomRepository.delete(chatRoom);
      return;
    }

    applicationEventPublisher.publishEvent(new ChatRoomAccessRevoked(roomId, member.id()));
    chatParticipantRepository.delete(chatParticipant);

    sendParticipantUpdateMessage(chatRoom);

    String systemMessage = member.nickname() + " 님이 퇴장하셨습니다.";
    chatMessageService.sendSystemMessage(roomId, systemMessage);
  }

  @Transactional
  public void joinChatRoom(Long roomId, String email) {
    ChatRoom chatRoom =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    MemberChatProfile member = memberService.findChatProfileByEmail(email);

    if (kickedParticipantRepository.existsByChatRoomAndMemberId(chatRoom, member.id())) {
      throw new CatxiException(ChatParticipantErrorCode.BLOCKED_FROM_ROOM);
    }

    ensureMemberIsNotInOtherRoom(member);

    if (chatRoom.getStatus() != RoomStatus.WAITING)
      throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);

    long current = chatParticipantRepository.countByChatRoom(chatRoom);
    if (current >= chatRoom.getMaxCapacity() + 1)
      throw new CatxiException(ChatRoomErrorCode.CHATROOM_FULL);

    ChatParticipant chatParticipant =
        ChatParticipant.builder().chatRoom(chatRoom).memberId(member.id()).build();

    saveParticipant(chatParticipant);

    sendParticipantUpdateMessage(chatRoom);

    chatMessageService.sendSystemMessage(roomId, member.nickname() + " 님이 입장하셨습니다.");
  }

  @Transactional(readOnly = true)
  public Long getMyChatRoomId(String email) {
    return findMyChatRoomId(email)
        .orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public Optional<Long> findMyChatRoomId(String email) {
    MemberChatProfile member = memberService.findChatProfileByEmail(email);

    return chatParticipantRepository
        .findByMemberId(member.id())
        .map(participant -> participant.getChatRoom().getRoomId());
  }

  @Transactional
  public void kickUser(Long roomId, String requesterEmail, String targetEmail) {
    ChatRoom room =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    MemberChatProfile requester = memberService.findChatProfileByEmail(requesterEmail);

    MemberChatProfile target = memberService.findChatProfileByEmail(targetEmail);

    if (!room.getHostId().equals(requester.id())) {
      throw new CatxiException(ChatRoomErrorCode.NOT_HOST);
    }
    if (requester.id().equals(target.id())) {
      throw new CatxiException(ChatParticipantErrorCode.CANNOT_KICK_SELF);
    }

    ChatParticipant participant =
        chatParticipantRepository
            .findByChatRoomAndMemberId(room, target.id())
            .orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

    log.debug(
        "[강퇴 처리] roomId: {}, requesterId: {}, targetId: {}", roomId, requester.id(), target.id());

    applicationEventPublisher.publishEvent(new ChatRoomAccessRevoked(roomId, target.id()));
    chatParticipantRepository.delete(participant);

    KickedParticipant kicked =
        KickedParticipant.builder().chatRoom(room).memberId(target.id()).build();
    kickedParticipantRepository.save(kicked);

    sendParticipantUpdateMessage(room);

    String msg = target.nickname() + " 님이 강퇴되었습니다.";
    chatMessageService.sendSystemMessage(roomId, msg);

    applicationEventPublisher.publishEvent(new ChatParticipantKicked(target.email()));
  }

  private void ensureMemberIsNotInOtherRoom(MemberChatProfile host) {
    boolean exists = chatParticipantRepository.existsByMemberId(host.id());
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
    MemberChatProfile member = memberService.findChatProfileByEmail(email);
    ChatRoom chatRoom =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    return chatRoom.getHostId().equals(member.id());
  }

  @Transactional
  public boolean isRoomParticipant(String email, Long roomId) {
    ChatRoom chatRoom =
        chatRoomRepository
            .findByIdForShare(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    MemberChatProfile member = memberService.findChatProfileByEmail(email);
    return chatParticipantRepository.existsByChatRoomAndMemberId(chatRoom, member.id());
  }

  @Transactional(readOnly = true)
  public void checkRoomEnter(Long roomId, String email) {
    ChatRoom chatRoom =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    MemberChatProfile member = memberService.findChatProfileByEmail(email);
    boolean isParticipant =
        chatParticipantRepository.existsByChatRoomAndMemberId(chatRoom, member.id());
    if (chatRoom.getStatus() == RoomStatus.READY_LOCKED && !isParticipant) {
      throw new CatxiException(ChatRoomErrorCode.CHATROOM_READY_LOCKED);
    }
    if (!isParticipant) {
      throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
    }
  }

  @Transactional(readOnly = true)
  public boolean hasParticipant(Long roomId, Long memberId) {
    return chatParticipantRepository.existsByChatRoom_RoomIdAndMemberId(roomId, memberId);
  }

  @Transactional(readOnly = true)
  public RoomDetails getChatRoomInfo(Long roomId, String email) {
    ChatRoom chatRoom =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    boolean isParticipant = isRoomParticipant(email, roomId);

    if (!isParticipant) {
      throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
    }

    List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(chatRoom);
    Map<Long, MemberChatProfile> profiles = findMemberProfiles(participants);
    List<String> participantEmails =
        participants.stream()
            .map(participant -> profiles.get(participant.getMemberId()).email())
            .toList();
    List<String> participantNicknames =
        participants.stream()
            .map(participant -> profiles.get(participant.getMemberId()).nickname())
            .toList();
    MemberChatProfile host = memberService.findChatProfileById(chatRoom.getHostId());

    return new RoomDetails(
        (long) participantEmails.size(),
        chatRoom.getMaxCapacity(),
        chatRoom.getStatus(),
        host.email(),
        host.nickname(),
        participantEmails,
        participantNicknames,
        chatRoom.getStartPoint(),
        chatRoom.getEndPoint(),
        chatRoom.getDepartAt());
  }

  private void sendParticipantUpdateMessage(ChatRoom chatRoom) {
    List<ChatParticipant> chatParticipants = chatParticipantRepository.findByChatRoom(chatRoom);
    Map<Long, MemberChatProfile> profiles = findMemberProfiles(chatParticipants);
    List<ChatParticipantSummary> participants =
        chatParticipants.stream()
            .map(
                participant -> {
                  MemberChatProfile profile = profiles.get(participant.getMemberId());
                  return new ChatParticipantSummary(profile.nickname(), profile.email());
                })
            .toList();
    applicationEventPublisher.publishEvent(
        new ChatRoomParticipantsUpdated(chatRoom.getRoomId(), participants));
  }

  private Map<Long, MemberChatProfile> findMemberProfiles(List<ChatParticipant> participants) {
    return memberService
        .findChatProfilesByIds(
            participants.stream().map(ChatParticipant::getMemberId).distinct().toList())
        .stream()
        .collect(Collectors.toMap(MemberChatProfile::id, profile -> profile));
  }

  public record CreatedRoom(
      Long roomId,
      MeetingPoint startPoint,
      MeetingPoint endPoint,
      Long recruitSize,
      LocalDateTime departAt,
      RoomStatus status) {}

  public record RoomListItem(
      Long roomId,
      Long hostId,
      String hostName,
      String hostNickname,
      Integer matchCount,
      MeetingPoint startPoint,
      MeetingPoint endPoint,
      Long recruitSize,
      Long currentSize,
      RoomStatus status,
      LocalDateTime departAt,
      LocalDateTime createdTime) {}

  public record RoomPage(
      Long myRoomId,
      List<RoomListItem> rooms,
      int totalPages,
      long totalElements,
      int currentPage,
      int size,
      boolean hasNext) {
    public RoomPage {
      rooms = List.copyOf(rooms);
    }
  }

  public record RoomDetails(
      Long currentSize,
      Long recruitSize,
      RoomStatus roomStatus,
      String hostEmail,
      String hostNickname,
      List<String> participantEmails,
      List<String> participantNicknames,
      MeetingPoint startPoint,
      MeetingPoint endPoint,
      LocalDateTime departAt) {}
}
