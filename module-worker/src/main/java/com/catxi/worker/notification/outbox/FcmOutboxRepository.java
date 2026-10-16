package com.catxi.worker.notification.outbox;

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
public class FcmOutboxRepository {

  private static final int MAX_CLAIM_BATCH_SIZE = 1_000;

  private static final String CLAIM_SQL =
      """
      SELECT id, event_id, event_type, CAST(payload AS CHAR) AS payload, retry_count
      FROM fcm_outbox
      WHERE status = 'PENDING'
        AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP(6))
        AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP(6))
      ORDER BY next_attempt_at, created_at, id
      LIMIT :batchSize
      FOR UPDATE SKIP LOCKED
      """;

  private final DatabaseClient databaseClient;
  private final TransactionalOperator transactionOperator;
  private final int leaseSeconds;

  public FcmOutboxRepository(
      DatabaseClient databaseClient,
      TransactionalOperator transactionOperator,
      @Value("${fcm.outbox.lease-duration}") java.time.Duration leaseDuration) {
    this.databaseClient = databaseClient;
    this.transactionOperator = transactionOperator;
    this.leaseSeconds = Math.toIntExact(Math.max(1, leaseDuration.toSeconds()));
  }

  public Mono<List<ClaimedFcmOutboxEvent>> claimBatch(String leaseOwner, int batchSize) {
    int boundedBatchSize = Math.min(MAX_CLAIM_BATCH_SIZE, Math.max(1, batchSize));
    Mono<List<ClaimedFcmOutboxEvent>> claim =
        databaseClient
            .sql(CLAIM_SQL)
            .bind("batchSize", boundedBatchSize)
            .map(
                (row, metadata) ->
                    new ClaimedFcmOutboxEvent(
                        Objects.requireNonNull(row.get("id", Long.class)),
                        Objects.requireNonNull(row.get("event_id", String.class)),
                        Objects.requireNonNull(row.get("event_type", String.class)),
                        Objects.requireNonNull(row.get("payload", String.class)),
                        Objects.requireNonNullElse(row.get("retry_count", Integer.class), 0)))
            .all()
            .collectList()
            .flatMap(
                events -> {
                  if (events.isEmpty()) {
                    return Mono.just(events);
                  }
                  return lease(events, leaseOwner)
                      .flatMap(
                          rowsUpdated ->
                              rowsUpdated == events.size()
                                  ? Mono.just(events)
                                  : Mono.error(
                                      new IllegalStateException(
                                          "FCM 아웃박스 배치의 선점 만료 시각 갱신 건수가 조회 건수와 일치하지 않습니다")));
                });

    return transactionOperator.transactional(claim);
  }

  private Mono<Long> lease(List<ClaimedFcmOutboxEvent> events, String leaseOwner) {
    String idParameters =
        IntStream.range(0, events.size())
            .mapToObj(index -> ":id" + index)
            .collect(Collectors.joining(", "));
    var statement =
        databaseClient
            .sql(
                """
                UPDATE fcm_outbox
                SET lease_owner = :leaseOwner,
                    lease_until = TIMESTAMPADD(SECOND, :leaseSeconds, CURRENT_TIMESTAMP(6))
                WHERE status = 'PENDING' AND id IN (%s)
                """
                    .formatted(idParameters))
            .bind("leaseOwner", leaseOwner)
            .bind("leaseSeconds", leaseSeconds);
    for (int index = 0; index < events.size(); index++) {
      statement = statement.bind("id" + index, events.get(index).id());
    }
    return statement.fetch().rowsUpdated();
  }

  public Mono<Long> markSent(long id, String leaseOwner) {
    return databaseClient
        .sql(
            """
            UPDATE fcm_outbox
            SET status = 'SENT', sent_at = CURRENT_TIMESTAMP(6), failed_at = NULL,
                error_message = NULL, lease_owner = NULL, lease_until = NULL
            WHERE id = :id AND status = 'PENDING' AND lease_owner = :leaseOwner
            """)
        .bind("id", id)
        .bind("leaseOwner", leaseOwner)
        .fetch()
        .rowsUpdated();
  }

  public Mono<Long> scheduleRetry(
      long id, String leaseOwner, int retryCount, int delaySeconds, String errorMessage) {
    return databaseClient
        .sql(
            """
            UPDATE fcm_outbox
            SET retry_count = :retryCount,
                next_attempt_at = TIMESTAMPADD(SECOND, :delaySeconds, CURRENT_TIMESTAMP(6)),
                error_message = :errorMessage,
                lease_owner = NULL, lease_until = NULL
            WHERE id = :id AND status = 'PENDING' AND lease_owner = :leaseOwner
            """)
        .bind("retryCount", retryCount)
        .bind("delaySeconds", delaySeconds)
        .bind("errorMessage", errorMessage)
        .bind("id", id)
        .bind("leaseOwner", leaseOwner)
        .fetch()
        .rowsUpdated();
  }

  public Mono<Long> markFailed(long id, String leaseOwner, int retryCount, String errorMessage) {
    return databaseClient
        .sql(
            """
            UPDATE fcm_outbox
            SET status = 'FAILED', failed_at = CURRENT_TIMESTAMP(6), retry_count = :retryCount,
                error_message = :errorMessage,
                lease_owner = NULL, lease_until = NULL
            WHERE id = :id AND status = 'PENDING' AND lease_owner = :leaseOwner
            """)
        .bind("retryCount", retryCount)
        .bind("errorMessage", errorMessage)
        .bind("id", id)
        .bind("leaseOwner", leaseOwner)
        .fetch()
        .rowsUpdated();
  }

  public Mono<Void> insertReplayEvent(String eventId, String eventType, String payload) {
    return databaseClient
        .sql(
            """
            INSERT INTO fcm_outbox
                (event_id, event_type, payload, status, retry_count, created_at)
            VALUES (:eventId, :eventType, CAST(:payload AS JSON), 'PENDING', 0, CURRENT_TIMESTAMP(6))
            """)
        .bind("eventId", eventId)
        .bind("eventType", eventType)
        .bind("payload", payload)
        .fetch()
        .rowsUpdated()
        .flatMap(
            rowsUpdated ->
                rowsUpdated == 1
                    ? Mono.empty()
                    : Mono.error(new IllegalStateException("FCM 재처리 아웃박스가 생성되지 않았습니다")));
  }

  public Mono<Boolean> existsByEventId(String eventId) {
    return databaseClient
        .sql("SELECT 1 FROM fcm_outbox WHERE event_id = :eventId LIMIT 1")
        .bind("eventId", eventId)
        .fetch()
        .first()
        .hasElement();
  }

  public Mono<Long> deleteSentBatch(int retentionSeconds, int batchSize) {
    return databaseClient
        .sql(
            """
            DELETE FROM fcm_outbox
            WHERE status = 'SENT'
              AND sent_at < TIMESTAMPADD(SECOND, -:retentionSeconds, CURRENT_TIMESTAMP(6))
            ORDER BY sent_at, id
            LIMIT :batchSize
            """)
        .bind("retentionSeconds", retentionSeconds)
        .bind("batchSize", batchSize)
        .fetch()
        .rowsUpdated();
  }

  public Mono<Long> deleteFailedBatch(int retentionSeconds, int batchSize) {
    return databaseClient
        .sql(
            """
            DELETE FROM fcm_outbox
            WHERE status = 'FAILED'
              AND failed_at < TIMESTAMPADD(SECOND, -:retentionSeconds, CURRENT_TIMESTAMP(6))
            ORDER BY failed_at, id
            LIMIT :batchSize
            """)
        .bind("retentionSeconds", retentionSeconds)
        .bind("batchSize", batchSize)
        .fetch()
        .rowsUpdated();
  }
}
