package com.catxi.chat.domain;

import com.catxi.common.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class ChatParticipant extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chat_room_id", nullable = false)
  private ChatRoom chatRoom;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @JdbcTypeCode(SqlTypes.TINYINT)
  private boolean isReady;

  @Column(nullable = false)
  @JdbcTypeCode(SqlTypes.TINYINT)
  private boolean isHost;

  public void setReady(boolean ready) {
    isReady = ready;
  }
}
