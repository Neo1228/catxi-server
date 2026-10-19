package com.catxi.member.domain;

import com.catxi.common.domain.MeetingPoint;
import com.catxi.common.persistence.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class MatchHistory extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "room_id")
  private Long roomId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MeetingPoint startPoint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MeetingPoint endPoint;

  @BatchSize(size = 50)
  @ElementCollection
  @CollectionTable(
      name = "match_history_participants",
      joinColumns = @JoinColumn(name = "history_id"))
  @Column(name = "participants", nullable = false)
  @Builder.Default
  private List<String> participants = new ArrayList<>();

  @Column(nullable = false)
  private LocalDateTime matchedAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt;
}
