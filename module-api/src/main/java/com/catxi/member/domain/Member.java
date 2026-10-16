package com.catxi.member.domain;

import com.catxi.common.persistence.BaseTimeEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@DynamicUpdate
@Table(
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_member_email", columnNames = "email"),
      @UniqueConstraint(name = "uk_member_fcm_fid", columnNames = "fcm_fid")
    })
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 외부 JSON 키와 기존 DB 컬럼명을 유지합니다.
  @JsonProperty("membername")
  @Column(name = "membername", nullable = false, length = 30)
  private String memberName;

  // 닉네임
  @Column(nullable = true, length = 30, unique = true)
  private String nickname;

  // 이메일
  @Column(nullable = false, length = 254)
  private String email;

  // 학번
  @Column(nullable = true, length = 20, unique = true)
  private String studentNo;

  private String role;

  // 조회 기록
  @Column(nullable = false)
  private int matchCount;

  @Column(nullable = false, updatable = false)
  @CreationTimestamp
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false)
  @JdbcTypeCode(SqlTypes.TINYINT)
  private boolean isLogin;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private MemberStatus status;

  // FCM 토큰
  // 워커가 별도 프로세스에서 토큰을 정리하므로 다른 회원 필드 저장이 값을 덮지 않게 합니다.
  @Column(name = "fcm_token", columnDefinition = "TEXT")
  private String fcmToken;

  @Column(name = "fcm_token_updated_at")
  private LocalDateTime fcmTokenUpdatedAt;

  @Column(name = "fcm_fid", length = 255)
  private String fcmFid;

  @Column(name = "fcm_fid_updated_at")
  private LocalDateTime fcmFidUpdatedAt;

  // 로그인했음 표시
  public void setLogin(boolean isLogin) {
    this.isLogin = isLogin;
  }

  // 삭제 필드
  public void delete() {
    this.status = MemberStatus.INACTIVE;
  }

  // FCM 토큰 업데이트
  public void updateFcmToken(String token) {
    if (fcmFid != null && token != null) {
      return;
    }
    this.fcmToken = token;
    this.fcmTokenUpdatedAt = LocalDateTime.now();
  }

  public void updateFcmFid(String fid) {
    this.fcmFid = fid;
    this.fcmFidUpdatedAt = LocalDateTime.now();
    this.fcmToken = null;
    this.fcmTokenUpdatedAt = null;
  }

  public void clearFcmRegistration() {
    this.fcmToken = null;
    this.fcmTokenUpdatedAt = null;
    this.fcmFid = null;
    this.fcmFidUpdatedAt = null;
  }
}
