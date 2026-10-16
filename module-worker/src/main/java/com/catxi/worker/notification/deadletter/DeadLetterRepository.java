package com.catxi.worker.notification.deadletter;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class DeadLetterRepository {

  private static final int MAX_ERROR_LENGTH = 4000;

  private final DatabaseClient databaseClient;

  public DeadLetterRepository(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<Void> save(
      String sourceMessageId, String eventId, String payload, String errorMessage, int retryCount) {
    String safeError =
        errorMessage == null || errorMessage.isBlank()
            ? "알 수 없는 알림 처리 오류"
            : errorMessage.substring(0, Math.min(MAX_ERROR_LENGTH, errorMessage.length()));
    DatabaseClient.GenericExecuteSpec statement =
        databaseClient
            .sql(
                """
                INSERT INTO fcm_dead_letter
                            (source_message_id, event_id, payload, error_message, retry_count, failed_at)
                        VALUES (:sourceMessageId, :eventId, :payload, :errorMessage,
                                :retryCount, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE id = id
                        """)
            .bind("sourceMessageId", sourceMessageId)
            .bind("payload", payload == null ? "" : payload)
            .bind("errorMessage", safeError)
            .bind("retryCount", Math.max(0, retryCount));

    if (eventId == null || eventId.isBlank()) {
      statement = statement.bindNull("eventId", String.class);
    } else {
      statement = statement.bind("eventId", eventId);
    }
    return statement.fetch().rowsUpdated().then();
  }

  public Mono<DeadLetterRecord> findForReplay(long id) {
    return databaseClient
        .sql(
            """
            SELECT id, event_id, CAST(payload AS CHAR) AS payload, replay_event_id
            FROM fcm_dead_letter
            WHERE id = :id
            FOR UPDATE
            """)
        .bind("id", id)
        .map(
            (row, metadata) ->
                new DeadLetterRecord(
                    row.get("id", Long.class),
                    row.get("event_id", String.class),
                    row.get("payload", String.class),
                    row.get("replay_event_id", String.class)))
        .one();
  }

  public Mono<Long> markReplayRequested(long id, String replayEventId) {
    return databaseClient
        .sql(
            """
            UPDATE fcm_dead_letter
            SET replay_event_id = :replayEventId,
                replay_requested_at = CURRENT_TIMESTAMP(6)
            WHERE id = :id AND replay_event_id IS NULL
            """)
        .bind("replayEventId", replayEventId)
        .bind("id", id)
        .fetch()
        .rowsUpdated();
  }

  public Mono<Long> deleteExpiredBatch(int retentionSeconds, int batchSize) {
    return databaseClient
        .sql(
            """
            DELETE FROM fcm_dead_letter
            WHERE failed_at < TIMESTAMPADD(SECOND, -:retentionSeconds, CURRENT_TIMESTAMP(6))
            ORDER BY failed_at, id
            LIMIT :batchSize
            """)
        .bind("retentionSeconds", retentionSeconds)
        .bind("batchSize", batchSize)
        .fetch()
        .rowsUpdated();
  }

  public record DeadLetterRecord(Long id, String eventId, String payload, String replayEventId) {}
}
