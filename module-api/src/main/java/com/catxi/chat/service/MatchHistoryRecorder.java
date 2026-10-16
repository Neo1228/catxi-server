package com.catxi.chat.service;

import com.catxi.chat.domain.ChatParticipant;
import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.domain.RoomStatus;
import com.catxi.chat.repository.ChatParticipantRepository;
import com.catxi.member.service.MatchHistoryService;
import com.catxi.member.service.MemberService;
import com.catxi.member.service.MemberService.MemberChatProfile;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchHistoryRecorder {

  private final ChatParticipantRepository chatParticipantRepository;
  private final MatchHistoryService matchHistoryService;
  private final MemberService memberService;

  public void recordMatchedRoom(ChatRoom room) {
    if (room.getStatus() != RoomStatus.MATCHED) {
      return;
    }

    List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(room);
    Map<Long, MemberChatProfile> profiles =
        memberService
            .findChatProfilesByIds(participants.stream().map(ChatParticipant::getMemberId).toList())
            .stream()
            .collect(Collectors.toMap(MemberChatProfile::id, profile -> profile));
    List<MatchHistoryService.MatchParticipant> matchParticipants =
        participants.stream()
            .map(
                participant ->
                    new MatchHistoryService.MatchParticipant(
                        participant.getMemberId(),
                        profiles.get(participant.getMemberId()).nickname()))
            .toList();

    matchHistoryService.recordMatchedRoom(
        room.getRoomId(),
        room.getStartPoint(),
        room.getEndPoint(),
        room.getMatchedAt(),
        matchParticipants);
  }
}
