package com.catxi.notification.fcm.domain;

import com.catxi.contract.notification.FcmNotificationEvent;
import com.catxi.contract.notification.FcmNotificationEvent.NotificationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "fcm_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmOutbox {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false, unique = true, length = 36)
  private String eventId;

  @Column(name = "event_type", nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  private NotificationType eventType;

  @Column(name = "payload", nullable = false, columnDefinition = "JSON")
  @JdbcTypeCode(SqlTypes.JSON)
  private FcmNotificationEvent payload;

  @Column(name = "status", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private OutboxStatus status = OutboxStatus.PENDING;

  @Column(name = "retry_count", nullable = false)
  private Integer retryCount = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "sent_at")
  private LocalDateTime sentAt;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Builder
  public FcmOutbox(String eventId, NotificationType eventType, FcmNotificationEvent payload) {
    this.eventId = eventId;
    this.eventType = eventType;
    this.payload = payload;
    this.status = OutboxStatus.PENDING;
    this.retryCount = 0;
    this.createdAt = LocalDateTime.now();
  }

  public enum OutboxStatus {
    PENDING, // 발행 대기 상태
    SENT, // 발행 완료
    FAILED // 발행 실패
  }
}
