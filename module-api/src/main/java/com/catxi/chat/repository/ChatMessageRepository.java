package com.catxi.chat.repository;

import com.catxi.chat.domain.ChatMessage;
import com.catxi.chat.domain.ChatRoom;
import com.catxi.member.domain.Member;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

  @EntityGraph(attributePaths = "member")
  List<ChatMessage> findByChatRoomOrderByCreatedTimeAscIdAsc(ChatRoom room, Pageable pageable);

  @Query(
"""
select message
from ChatMessage message
left join fetch message.member
where message.chatRoom = :room
order by message.createdTime desc, message.id desc
""")
  List<ChatMessage> findFirstHistoryPage(@Param("room") ChatRoom room, Pageable pageable);

  @Query(
"""
select message
from ChatMessage message
left join fetch message.member
where message.chatRoom = :room
and (message.createdTime < :beforeCreatedAt
     or (message.createdTime = :beforeCreatedAt and message.id < :beforeId))
order by message.createdTime desc, message.id desc
""")
  List<ChatMessage> findHistoryBefore(
      @Param("room") ChatRoom room,
      @Param("beforeCreatedAt") LocalDateTime beforeCreatedAt,
      @Param("beforeId") Long beforeMessageId,
      Pageable pageable);

  @Query(
      """
            select message
            from ChatMessage message
            left join fetch message.member
            where message.chatRoom = :room
              and message.id > :afterMessageId
            order by message.id asc
            """)
  List<ChatMessage> findMessagesAfterId(
      @Param("room") ChatRoom room,
      @Param("afterMessageId") Long afterMessageId,
      Pageable pageable);

  Optional<ChatMessage> findByChatRoomAndMemberAndClientMessageId(
      ChatRoom room, Member member, UUID clientMessageId);

  @Modifying
  @Query(value = "DELETE FROM chat_message WHERE chat_room_id IN (:roomIds)", nativeQuery = true)
  int deleteByChatRoomIds(@Param("roomIds") List<Long> roomIds);

  Optional<ChatMessage> findTopByChatRoomAndMemberOrderByCreatedTimeDesc(
      ChatRoom chatRoom, Member member);
}
