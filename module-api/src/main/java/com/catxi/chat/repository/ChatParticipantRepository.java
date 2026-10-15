package com.catxi.chat.repository;

import com.catxi.chat.domain.ChatParticipant;
import com.catxi.chat.domain.ChatRoom;
import com.catxi.member.domain.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

  boolean existsByMember(Member member);

  boolean existsByChatRoomAndMember(ChatRoom room, Member member);

  Optional<ChatParticipant> findByChatRoomAndMember(ChatRoom room, Member member);

  long countByChatRoom(ChatRoom room);

  long countByChatRoomAndIsReady(ChatRoom chatRoom, boolean ready);

  List<ChatParticipant> findByChatRoom(ChatRoom chatRoom);

  @Query(
      "SELECT participant.member.id FROM ChatParticipant participant WHERE participant.chatRoom = :chatRoom")
  List<Long> findMemberIdsByChatRoom(@Param("chatRoom") ChatRoom chatRoom);

  @Query(
      "SELECT participant.member.id FROM ChatParticipant participant "
          + "WHERE participant.chatRoom = :chatRoom AND participant.member.id <> :senderId")
  List<Long> findRecipientMemberIdsByChatRoom(
      @Param("chatRoom") ChatRoom chatRoom, @Param("senderId") Long senderId);

  @Query(
      "SELECT member.email AS email, member.memberName AS memberName, member.nickname AS nickname "
          + "FROM ChatParticipant participant JOIN participant.member member "
          + "WHERE participant.chatRoom = :chatRoom")
  List<ChatParticipantSummary> findSummariesByChatRoom(@Param("chatRoom") ChatRoom chatRoom);

  interface ChatParticipantSummary {
    String getEmail();

    String getMemberName();

    String getNickname();
  }

  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      "UPDATE ChatParticipant cp SET cp.isReady = false WHERE cp.chatRoom.roomId = :roomId AND cp.isHost = false")
  void clearReadyStatusForNonHosts(Long roomId);

  @Transactional
  void deleteAllByChatRoomAndIsReadyFalse(ChatRoom chatroom);

  @Query(
      "SELECT cp.member.nickname FROM ChatParticipant cp WHERE cp.chatRoom = :chatRoom AND cp.isReady = false")
  List<String> findNicknamesByChatRoomAndIsReadyFalse(ChatRoom chatRoom);

  Optional<ChatParticipant> findByMember(Member member);
}
