package com.catxi.chat.domain;

import com.catxi.common.domain.Location;
import com.catxi.common.persistence.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long roomId;

  @Column(name = "host_id", nullable = false)
  private Long hostId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Location startPoint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Location endPoint;

  @Column(nullable = false)
  private LocalDateTime departAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RoomStatus status;

  @Column(nullable = false)
  private Long maxCapacity; // 1~4

  @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
  private final List<ChatParticipant> participants = new ArrayList<>();

  @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<KickedParticipant> kickedParticipants = new ArrayList<>();

  @Column private LocalDateTime matchedAt;

  @Column(name = "ready_timeout_at")
  private LocalDateTime readyTimeoutAt;

  @Column(name = "ready_participant_count")
  private Long readyParticipantCount;

  public void lockForReady(LocalDateTime timeoutAt, long participantCount) {
    this.status = RoomStatus.READY_LOCKED;
    this.readyTimeoutAt = timeoutAt;
    this.readyParticipantCount = participantCount;
  }

  public void setStatus(RoomStatus status) {
    this.status = status;
    if (status != RoomStatus.READY_LOCKED) {
      this.readyTimeoutAt = null;
      this.readyParticipantCount = null;
    }
  }

  public void matchedStatus(RoomStatus newStatus) {
    if (status == RoomStatus.READY_LOCKED && this.matchedAt == null) {
      this.matchedAt = LocalDateTime.now();
    }
    setStatus(newStatus);
  }
}
