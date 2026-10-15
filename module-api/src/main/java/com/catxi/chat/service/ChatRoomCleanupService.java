package com.catxi.chat.service;

import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.domain.RoomStatus;
import com.catxi.chat.dto.RoomDeletedEvent;
import com.catxi.chat.repository.ChatMessageRepository;
import com.catxi.chat.repository.ChatRoomRepository;
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
public class ChatRoomCleanupService {

  private static final int BATCH_SIZE = 100;

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final MatchHistoryRecorder matchHistoryRecorder;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public int deleteExpiredChatRoomsBatch() {
    List<ChatRoom> expiredRooms =
        chatRoomRepository.findExpiredBefore(LocalDateTime.now(), BATCH_SIZE);

    if (expiredRooms.isEmpty()) {
      return 0;
    }

    List<Long> roomIds = expiredRooms.stream().map(ChatRoom::getRoomId).toList();
    List<RoomDeletedEvent> deletionEvents =
        expiredRooms.stream().map(room -> new RoomDeletedEvent(room.getRoomId(), null)).toList();
    expiredRooms.stream()
        .filter(room -> room.getStatus() == RoomStatus.MATCHED)
        .forEach(matchHistoryRecorder::recordForExpiredRoom);
    int deletedMessages = chatMessageRepository.deleteByChatRoomIds(roomIds);
    chatRoomRepository.deleteAll(expiredRooms);
    deletionEvents.forEach(eventPublisher::publishEvent);
    log.info("만료된 채팅방 배치 정리 완료: 방 {}개, 메시지 {}개", expiredRooms.size(), deletedMessages);
    return expiredRooms.size();
  }
}
