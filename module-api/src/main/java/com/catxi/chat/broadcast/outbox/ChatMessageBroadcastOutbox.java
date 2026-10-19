package com.catxi.chat.broadcast.outbox;

import com.catxi.chat.broadcast.message.ChatMessageBroadcast;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "chat_message_broadcast_outbox",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_chat_message_broadcast_message_id",
            columnNames = "message_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageBroadcastOutbox {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "message_id", nullable = false)
  private Long messageId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "json")
  private ChatMessageBroadcast payload;

  public ChatMessageBroadcastOutbox(ChatMessageBroadcast payload) {
    this.payload = Objects.requireNonNull(payload);
    this.messageId = Objects.requireNonNull(payload.messageId());
  }
}
