package com.catxi.chat.repository;

import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.domain.RoomStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ChatRoomRepository
    extends JpaRepository<ChatRoom, Long>, ChatRoomRepositoryCustom {
  @Query(
      value =
          """
    SELECT * FROM chat_room
    WHERE depart_at < :deadline
    ORDER BY depart_at, room_id
    LIMIT :batchSize
    FOR UPDATE SKIP LOCKED
    """,
      nativeQuery = true)
  List<ChatRoom> findExpiredBefore(
      @Param("deadline") LocalDateTime deadline, @Param("batchSize") int batchSize);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT room FROM ChatRoom room WHERE room.roomId = :roomId")
  Optional<ChatRoom> findByIdForUpdate(@Param("roomId") Long roomId);

  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("SELECT room FROM ChatRoom room WHERE room.roomId = :roomId")
  Optional<ChatRoom> findByIdForShare(@Param("roomId") Long roomId);

  @Query(
      "SELECT room.roomId FROM ChatRoom room "
          + "WHERE room.status = :status AND room.readyTimeoutAt <= :deadline "
          + "ORDER BY room.readyTimeoutAt, room.roomId")
  List<Long> findDueReadyTimeoutIds(
      @Param("status") RoomStatus status,
      @Param("deadline") LocalDateTime deadline,
      Pageable pageable);

  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      "UPDATE ChatRoom room SET room.readyTimeoutAt = :retryAt "
          + "WHERE room.roomId = :roomId AND room.status = :status AND room.readyTimeoutAt <= :now")
  int postponeDueReadyTimeout(
      @Param("roomId") Long roomId,
      @Param("status") RoomStatus status,
      @Param("now") LocalDateTime now,
      @Param("retryAt") LocalDateTime retryAt);
}
