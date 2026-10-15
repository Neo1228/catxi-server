package com.catxi.chat.service;

import com.catxi.chat.domain.ChatParticipant;
import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.domain.RoomStatus;
import com.catxi.chat.repository.ChatParticipantRepository;
import com.catxi.member.domain.MatchHistory;
import com.catxi.member.domain.Member;
import com.catxi.member.repository.MatchHistoryRepository;
import com.catxi.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchHistoryRecorder {

  private final ChatParticipantRepository chatParticipantRepository;
  private final MemberRepository memberRepository;
  private final MatchHistoryRepository matchHistoryRepository;

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordForMatchedRoom(ChatRoom room) {
    recordMatchedRoom(room);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordForExpiredRoom(ChatRoom room) {
    recordMatchedRoom(room);
  }

  private void recordMatchedRoom(ChatRoom room) {
    List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(room);
    if (room.getStatus() != RoomStatus.MATCHED || participants.isEmpty()) {
      return;
    }

    List<String> participantNames =
        participants.stream().map(participant -> participant.getMember().getNickname()).toList();
    LocalDateTime createdAt = LocalDateTime.now();

    for (ChatParticipant participant : participants) {
      Member member = participant.getMember();
      if (matchHistoryRepository.existsByRoomIdAndUser_Id(room.getRoomId(), member.getId())) {
        continue;
      }
      matchHistoryRepository.save(
          MatchHistory.builder()
              .roomId(room.getRoomId())
              .user(member)
              .startPoint(room.getStartPoint())
              .endPoint(room.getEndPoint())
              .participants(participantNames)
              .createdAt(createdAt)
              .matchedAt(room.getMatchedAt())
              .build());
      memberRepository.incrementMatchCount(member.getId());
    }
  }
}
