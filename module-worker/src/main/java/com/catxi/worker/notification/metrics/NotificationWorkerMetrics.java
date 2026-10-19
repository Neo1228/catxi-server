package com.catxi.worker.notification.metrics;

import com.catxi.worker.notification.redis.RedisStreamStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "fcm.worker.enabled", havingValue = "true")
public class NotificationWorkerMetrics {

  private static final Logger log = LoggerFactory.getLogger(NotificationWorkerMetrics.class);
  private static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(30);
  private static final Duration SAMPLE_TIMEOUT = Duration.ofSeconds(10);
  // shortcut: 10,001건까지만 세어 DB 조회 비용을 제한하고, 경보는 그보다 낮은 임계치로 건다.
  private static final int METRIC_COUNT_LIMIT = 10_001;

  private final DatabaseClient databaseClient;
  private final RedisStreamStore streamStore;
  private final AtomicLong pendingCount = new AtomicLong();
  private final AtomicLong firstDueEventAgeSeconds = new AtomicLong();
  private final AtomicLong deadLetterFailuresLast15Minutes = new AtomicLong();
  private final AtomicLong redisStreamSize = new AtomicLong();
  private final AtomicLong redisStreamPending = new AtomicLong();
  private final AtomicLong redisStreamMaxBacklog;
  private final AtomicLong lastSuccessfulRefreshEpochSeconds = new AtomicLong();
  private Disposable subscription;

  public NotificationWorkerMetrics(
      DatabaseClient databaseClient,
      RedisStreamStore streamStore,
      @Value("${fcm.outbox.batch-size}") int batchSize,
      @Value("${fcm.stream.max-backlog}") long maxStreamBacklog,
      MeterRegistry meterRegistry) {
    this.databaseClient = databaseClient;
    this.streamStore = streamStore;
    this.redisStreamMaxBacklog = new AtomicLong(Math.max(Math.max(1, batchSize), maxStreamBacklog));
    Gauge.builder("catxi.fcm.outbox.pending.sampled", pendingCount, AtomicLong::doubleValue)
        .description("FCM 알림 아웃박스 대기 행 수. 최대 10,001건까지 집계합니다")
        .register(meterRegistry);
    Gauge.builder(
            "catxi.fcm.outbox.first.due.event.age.seconds",
            firstDueEventAgeSeconds,
            AtomicLong::doubleValue)
        .register(meterRegistry);
    Gauge.builder(
            "catxi.fcm.dead.letter.failures.15m",
            deadLetterFailuresLast15Minutes,
            AtomicLong::doubleValue)
        .register(meterRegistry);
    Gauge.builder("catxi.fcm.stream.size", redisStreamSize, AtomicLong::doubleValue)
        .register(meterRegistry);
    Gauge.builder("catxi.fcm.stream.pending", redisStreamPending, AtomicLong::doubleValue)
        .register(meterRegistry);
    Gauge.builder("catxi.fcm.stream.max.backlog", redisStreamMaxBacklog, AtomicLong::doubleValue)
        .register(meterRegistry);
    Gauge.builder(
            "catxi.worker.metrics.last.success.epoch.seconds",
            lastSuccessfulRefreshEpochSeconds,
            AtomicLong::doubleValue)
        .register(meterRegistry);
  }

  public synchronized void start() {
    if (subscription != null && !subscription.isDisposed()) {
      return;
    }
    subscription =
        Flux.interval(Duration.ZERO, SAMPLE_INTERVAL)
            .onBackpressureDrop()
            .concatMap(
                ignored ->
                    sample()
                        .timeout(SAMPLE_TIMEOUT)
                        .onErrorResume(
                            error -> {
                              log.warn("FCM 운영 지표 조회가 실패했습니다. 다음 주기에 다시 시도합니다", error);
                              return Mono.empty();
                            }),
                1)
            .subscribe();
  }

  public synchronized void stop() {
    if (subscription != null) {
      subscription.dispose();
      subscription = null;
    }
  }

  private Mono<Void> sample() {
    return Mono.zip(
            samplePendingCount(),
            sampleFirstDueEventAge(),
            sampleRecentDeadLetters(),
            streamStore.size(),
            streamStore.pendingCount())
        .doOnNext(
            sample -> {
              pendingCount.set(sample.getT1());
              firstDueEventAgeSeconds.set(sample.getT2());
              deadLetterFailuresLast15Minutes.set(sample.getT3());
              redisStreamSize.set(sample.getT4());
              redisStreamPending.set(sample.getT5());
              lastSuccessfulRefreshEpochSeconds.set(Instant.now().getEpochSecond());
            })
        .then();
  }

  private Mono<Long> samplePendingCount() {
    return databaseClient
        .sql(
            """
                        SELECT COUNT(*) AS pending_count
                        FROM (
                            SELECT id
                            FROM fcm_outbox
                            WHERE status IN ('PENDING', 'PUBLISHED')
                            LIMIT :countLimit
                        ) pending_sample
                        """)
        .bind("countLimit", METRIC_COUNT_LIMIT)
        .map((row, metadata) -> row.get("pending_count", Long.class))
        .one();
  }

  private Mono<Long> sampleFirstDueEventAge() {
    return databaseClient
        .sql(
            """
                        SELECT TIMESTAMPDIFF(SECOND, created_at, CURRENT_TIMESTAMP(6)) AS age_seconds
                        FROM fcm_outbox
                        WHERE status IN ('PENDING', 'PUBLISHED')
                          AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP(6))
                        ORDER BY next_attempt_at, created_at, id
                        LIMIT 1
                        """)
        .map((row, metadata) -> row.get("age_seconds", Long.class))
        .one()
        .defaultIfEmpty(0L);
  }

  private Mono<Long> sampleRecentDeadLetters() {
    return databaseClient
        .sql(
            """
                        SELECT COUNT(*) AS failure_count
                        FROM (
                            SELECT id
                            FROM fcm_dead_letter
                            WHERE failed_at >= CURRENT_TIMESTAMP(6) - INTERVAL 15 MINUTE
                            ORDER BY failed_at, id
                            LIMIT :countLimit
                        ) recent_failures
                        """)
        .bind("countLimit", METRIC_COUNT_LIMIT)
        .map((row, metadata) -> row.get("failure_count", Long.class))
        .one();
  }
}
