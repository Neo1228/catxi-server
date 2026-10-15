package com.catxi.report.domain;

import com.catxi.common.domain.BaseTimeEntity;
import com.catxi.member.domain.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_report_room_reporter_target",
            columnNames = {"room_id", "reporter_id", "reported_member_id"}))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "room_id", nullable = false)
  private Long roomId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reporter_id", nullable = false)
  private Member reporter;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reported_member_id", nullable = false)
  private Member reportedMember;

  @Column(nullable = false, length = 500)
  private String reason;
}
