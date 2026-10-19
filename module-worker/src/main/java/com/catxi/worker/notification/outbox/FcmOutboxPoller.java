package com.catxi.worker.notification.outbox;

import com.catxi.worker.notification.deadletter.DeadLetterRepository;
import com.catxi.worker.notification.redis.RedisStreamStore;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
@ConditionalOnProperty(name = "fcm.worker.enabled", havingValue = "true")
public class FcmOutboxPoller {

  private static final Logger log = LoggerFactory.getLogger(FcmOutboxPoller.class);
  private static final int MAX_CLEANUP_BATCHES_PER_RUN = 10;
  private static final Duration EMPTY_OUTBOX_DELAY = Duration.ofSeconds(2);

  private final FcmOutboxRepository outboxRepository;
  private final DeadLetterRepository deadLetterRepository;
  private final FcmOutboxPublisher outboxPublisher;
  private final RedisStreamStore streamStore;
  private final Duration pollInterval;
  private final int sentRetentionSeconds;
  private final int failureRetentionSeconds;
  private final int batchSize;
  private final int cleanupBatchSize;
  private final int publishConcurrency;
  private final long maxStreamBacklog;
  private final String leaseOwner = UUID.randomUUID().toString();
  private Disposable subscription;
  private Disposable cleanupSubscription;

  public FcmOutboxPoller(
      FcmOutboxRepository outboxRepository,
      DeadLetterRepository deadLetterRepository,
      FcmOutboxPublisher outboxPublisher,
      RedisStreamStore streamStore,
      @Value("${fcm.outbox.poll-interval}") Duration pollInterval,
      @Value("${fcm.outbox.sent-retention}") Duration sentRetention,
      @Value("${fcm.failure-retention}") Duration failureRetention,
      @Value("${fcm.outbox.batch-size}") int batchSize,
      @Value("${fcm.outbox.cleanup-batch-size}") int cleanupBatchSize,
      @Value("${fcm.outbox.publish-concurrency}") int publishConcurrency,
      @Value("${fcm.stream.max-backlog}") long maxStreamBacklog) {
    this.outboxRepository = outboxRepository;
    this.deadLetterRepository = deadLetterRepository;
    this.outboxPublisher = outboxPublisher;
    this.streamStore = streamStore;
    this.pollInterval = pollInterval;
    this.sentRetentionSeconds = Math.toIntExact(Math.max(1, sentRetention.toSeconds()));
    this.failureRetentionSeconds = Math.toIntExact(Math.max(1, failureRetention.toSeconds()));
    this.batchSize = Math.max(1, batchSize);
    this.cleanupBatchSize = Math.max(1, cleanupBatchSize);
    this.publishConcurrency = Math.max(1, publishConcurrency);
    this.maxStreamBacklog = Math.max(this.batchSize, maxStreamBacklog);
  }

  public synchronized void start() {
    if (subscription != null && !subscription.isDisposed()) {
      return;
    }
    subscription =
        Flux.interval(Duration.ZERO, pollInterval)
            .onBackpressureDrop()
            .concatMap(
                ignored ->
                    streamStore
                        .size()
                        .filter(size -> size <= maxStreamBacklog - batchSize)
                        .flatMapMany(
                            ignoredSize ->
                                outboxRepository
                                    .claimBatch(leaseOwner, batchSize)
                                    .flatMapMany(
                                        events -> {
                                          if (events.isEmpty()) {
                                            return Mono.delay(EMPTY_OUTBOX_DELAY)
                                                .thenMany(Flux.<Void>empty());
                                          }
                                          return Flux.fromIterable(events)
                                              .flatMap(
                                                  event ->
                                                      outboxPublisher.publish(event, leaseOwner),
                                                  publishConcurrency);
                                        }))
                        .doOnError(error -> log.error("FCM 아웃박스 폴링 단계가 실패했습니다", error))
                        .retryWhen(
                            Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                                .maxBackoff(Duration.ofSeconds(30))))
            .subscribe();
    cleanupSubscription =
        Flux.interval(Duration.ofHours(1), Duration.ofHours(1))
            .onBackpressureDrop()
            .concatMap(
                ignored ->
                    deleteExpiredRows()
                        .doOnNext(
                            deleted -> {
                              if (deleted > 0) {
                                log.info("보존 기간이 지난 FCM 전송 기록을 정리했습니다: count={}", deleted);
                              }
                            })
                        .onErrorResume(
                            error -> {
                              log.error("FCM 아웃박스 보존 행 정리가 실패했습니다", error);
                              return Mono.just(0L);
                            }))
            .subscribe();
  }

  private Mono<Long> deleteExpiredRows() {
    return deleteBatches(
            () -> outboxRepository.deleteSentBatch(sentRetentionSeconds, cleanupBatchSize))
        .flatMap(
            sent ->
                deleteBatches(
                        () ->
                            outboxRepository.deleteFailedBatch(
                                failureRetentionSeconds, cleanupBatchSize))
                    .flatMap(
                        failed ->
                            deleteBatches(
                                    () ->
                                        deadLetterRepository.deleteExpiredBatch(
                                            failureRetentionSeconds, cleanupBatchSize))
                                .map(deadLetter -> sent + failed + deadLetter)));
  }

  private Mono<Long> deleteBatches(Supplier<Mono<Long>> deleteBatch) {
    return Flux.range(0, MAX_CLEANUP_BATCHES_PER_RUN)
        .concatMap(
            batch ->
                (batch == 0 ? Mono.just(0L) : Mono.delay(Duration.ofMillis(50)))
                    .then(Mono.defer(deleteBatch)))
        .takeUntil(deleted -> deleted < cleanupBatchSize)
        .reduce(0L, Long::sum);
  }

  public synchronized void stop() {
    if (subscription != null) {
      subscription.dispose();
      subscription = null;
    }
    if (cleanupSubscription != null) {
      cleanupSubscription.dispose();
      cleanupSubscription = null;
    }
  }
}
