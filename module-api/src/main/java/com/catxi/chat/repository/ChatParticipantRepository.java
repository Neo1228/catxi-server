package com.catxi.chat.repository;

import com.catxi.chat.domain.ChatParticipant;
import com.catxi.chat.domain.ChatRoom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

  boolean existsByMemberId(Long memberId);

  boolean existsByChatRoomAndMemberId(ChatRoom room, Long memberId);

  boolean existsByChatRoom_RoomIdAndMemberId(Long roomId, Long memberId);

  Optional<ChatParticipant> findByChatRoomAndMemberId(ChatRoom room, Long memberId);

  long countByChatRoom(ChatRoom room);

  long countByChatRoomAndIsReady(ChatRoom chatRoom, boolean ready);

  List<ChatParticipant> findByChatRoom(ChatRoom chatRoom);

  @Query(
      "SELECT participant.memberId FROM ChatParticipant participant WHERE participant.chatRoom = :chatRoom")
  List<Long> findMemberIdsByChatRoom(@Param("chatRoom") ChatRoom chatRoom);

  @Query(
      "SELECT participant.memberId FROM ChatParticipant participant "
          + "WHERE participant.chatRoom = :chatRoom AND participant.memberId <> :senderId")
  List<Long> findRecipientMemberIdsByChatRoom(
      @Param("chatRoom") ChatRoom chatRoom, @Param("senderId") Long senderId);

  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      "UPDATE ChatParticipant cp SET cp.isReady = false WHERE cp.chatRoom.roomId = :roomId AND cp.isHost = false")
  void clearReadyStatusForNonHosts(Long roomId);

  @Transactional
  void deleteAllByChatRoomAndIsReadyFalse(ChatRoom chatroom);

  Optional<ChatParticipant> findByMemberId(Long memberId);
}
