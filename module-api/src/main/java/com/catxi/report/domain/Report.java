package com.catxi.report.domain;

import com.catxi.common.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "report",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_report_room_reporter_target",
            columnNames = {"room_id", "reporter_id", "reported_member_id"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Report extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "room_id", nullable = false)
  private Long roomId;

  @Column(name = "reporter_id", nullable = false)
  private Long reporterId;

  @Column(name = "reported_member_id", nullable = false)
  private Long reportedMemberId;

  @Column(nullable = false, length = 500)
  private String reason;
}
