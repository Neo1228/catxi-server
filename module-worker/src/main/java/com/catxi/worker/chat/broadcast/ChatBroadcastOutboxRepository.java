package com.catxi.worker.chat.broadcast;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Repository
public class ChatBroadcastOutboxRepository {

  private static final int MAX_CLAIM_BATCH_SIZE = 500;

  // DB 행 잠금은 선점 트랜잭션에서만 유지하고, 실제 Redis 발행은 별도 임대로 보호합니다.
  private static final String CLAIM_SQL =
      """
      SELECT id, message_id, CAST(payload AS CHAR) AS payload, retry_count
      FROM chat_message_broadcast_outbox
      WHERE status = 'PENDING'
        AND next_attempt_at <= CURRENT_TIMESTAMP(6)
        AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP(6))
      ORDER BY next_attempt_at, id
      LIMIT :batchSize
      FOR UPDATE SKIP LOCKED
      """;

  private final DatabaseClient databaseClient;
  private final TransactionalOperator transactionOperator;
  private final int leaseSeconds;

  public ChatBroadcastOutboxRepository(
      DatabaseClient databaseClient,
      TransactionalOperator transactionOperator,
      @Value("${chat.broadcast.outbox.lease-duration}") Duration leaseDuration) {
    this.databaseClient = databaseClient;
    this.transactionOperator = transactionOperator;
    this.leaseSeconds = Math.toIntExact(Math.max(1, leaseDuration.toSeconds()));
  }

  public Mono<List<ClaimedEvent>> claimDue(String leaseOwner, int requestedBatchSize) {
    int batchSize = Math.min(MAX_CLAIM_BATCH_SIZE, Math.max(1, requestedBatchSize));
    Mono<List<ClaimedEvent>> claim =
        databaseClient
            .sql(CLAIM_SQL)
            .bind("batchSize", batchSize)
            .map(
                (row, metadata) ->
                    new ClaimedEvent(
                        Objects.requireNonNull(row.get("id", Long.class)),
                        Objects.requireNonNull(row.get("message_id", Long.class)),
                        Objects.requireNonNull(row.get("payload", String.class)),
                        Objects.requireNonNullElse(row.get("retry_count", Long.class), 0L)))
            .all()
            .collectList()
            .flatMap(
                events -> {
                  if (events.isEmpty()) {
                    return Mono.just(events);
                  }
                  return lease(events, leaseOwner).thenReturn(events);
                });
    return transactionOperator.transactional(claim);
  }

  private Mono<Long> lease(List<ClaimedEvent> events, String leaseOwner) {
    String placeholders =
        IntStream.range(0, events.size())
            .mapToObj(index -> ":id" + index)
            .collect(Collectors.joining(", "));
    DatabaseClient.GenericExecuteSpec statement =
        databaseClient
            .sql(
                """
                UPDATE chat_message_broadcast_outbox
                SET lease_owner = :leaseOwner,
                    lease_until = TIMESTAMPADD(SECOND, :leaseSeconds, CURRENT_TIMESTAMP(6))
                WHERE status = 'PENDING'
                  AND next_attempt_at <= CURRENT_TIMESTAMP(6)
                  AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP(6))
                  AND id IN (
                """
                    + placeholders
                    + ")")
            .bind("leaseOwner", leaseOwner)
            .bind("leaseSeconds", leaseSeconds);
    for (int index = 0; index < events.size(); index++) {
      statement = statement.bind("id" + index, events.get(index).id());
    }
    return statement
        .fetch()
        .rowsUpdated()
        .flatMap(
            updated ->
                updated == events.size()
                    ? Mono.just(updated)
                    : Mono.error(new IllegalStateException("채팅 브로드캐스트 아웃박스 선점에 실패했습니다")));
  }

  public Mono<Long> markSent(long id, String leaseOwner) {
    return databaseClient
        .sql(
            """
            UPDATE chat_message_broadcast_outbox
            SET status = 'SENT',
                sent_at = CURRENT_TIMESTAMP(6),
                lease_owner = NULL,
                lease_until = NULL
            WHERE id = :id
              AND status = 'PENDING'
              AND lease_owner = :leaseOwner
            """)
        .bind("id", id)
        .bind("leaseOwner", leaseOwner)
        .fetch()
        .rowsUpdated();
  }

  public Mono<Long> scheduleRetry(
      ClaimedEvent event, String leaseOwner, long delaySeconds, String errorMessage) {
    return databaseClient
        .sql(
            """
            UPDATE chat_message_broadcast_outbox
            SET retry_count = CASE
                  WHEN retry_count < 9223372036854775807 THEN retry_count + 1
                  ELSE retry_count
                END,
                next_attempt_at = TIMESTAMPADD(SECOND, :delaySeconds, CURRENT_TIMESTAMP(6)),
                lease_owner = NULL,
                lease_until = NULL,
                error_message = :errorMessage
            WHERE id = :id
              AND status = 'PENDING'
              AND lease_owner = :leaseOwner
            """)
        .bind("delaySeconds", delaySeconds)
        .bind("errorMessage", errorMessage)
        .bind("id", event.id())
        .bind("leaseOwner", leaseOwner)
        .fetch()
        .rowsUpdated();
  }

  public Mono<Long> deleteSentBatch(long retentionSeconds, int batchSize) {
    return databaseClient
        .sql(
            """
            DELETE FROM chat_message_broadcast_outbox
            WHERE status = 'SENT'
              AND sent_at <= TIMESTAMPADD(SECOND, :cutoffOffsetSeconds, CURRENT_TIMESTAMP(6))
            ORDER BY sent_at, id
            LIMIT :batchSize
            """)
        .bind("cutoffOffsetSeconds", -retentionSeconds)
        .bind("batchSize", Math.max(1, batchSize))
        .fetch()
        .rowsUpdated();
  }

  public record ClaimedEvent(long id, long messageId, String payload, long retryCount) {}
}
