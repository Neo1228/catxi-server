package com.catxi.worker.notification.outbox;

import com.catxi.worker.notification.deadletter.DeadLetterRepository;
import com.catxi.worker.notification.stream.RedisStreamStore;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(name = "fcm.worker.enabled", havingValue = "true")
public class FcmOutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(FcmOutboxPublisher.class);
  private static final int MAX_ERROR_LENGTH = 4000;

  private final FcmOutboxRepository outboxRepository;
  private final DeadLetterRepository deadLetterRepository;
  private final RedisStreamStore streamStore;
  private final TransactionalOperator transactionOperator;
  private final int maxRetries;
  private final int baseDelaySeconds;
  private final int maxDelaySeconds;

  public FcmOutboxPublisher(
      FcmOutboxRepository outboxRepository,
      DeadLetterRepository deadLetterRepository,
      RedisStreamStore streamStore,
      TransactionalOperator transactionOperator,
      @Value("${fcm.outbox.max-retries}") int maxRetries,
      @Value("${fcm.outbox.retry-base-delay}") Duration baseDelay,
      @Value("${fcm.outbox.retry-max-delay}") Duration maxDelay) {
    this.outboxRepository = outboxRepository;
    this.deadLetterRepository = deadLetterRepository;
    this.streamStore = streamStore;
    this.transactionOperator = transactionOperator;
    this.maxRetries = Math.max(0, maxRetries);
    this.baseDelaySeconds = Math.toIntExact(Math.max(1, baseDelay.toSeconds()));
    this.maxDelaySeconds = Math.toIntExact(Math.max(baseDelaySeconds, maxDelay.toSeconds()));
  }

  public Mono<Void> publish(ClaimedFcmOutboxEvent event, String leaseOwner) {
    String publicationKey =
        event.eventId()
            + ":"
            + (event.publicationKey() == null ? "initial" : event.publicationKey());
    return streamStore
        .publishOutbox(publicationKey, event.eventId(), event.payload())
        .flatMap(
            messageId ->
                outboxRepository
                    .markPublished(event.id(), leaseOwner)
                    .doOnNext(
                        rows -> {
                          if (rows == 0) {
                            log.warn(
                                "FCM 아웃박스 선점 소유권이 바뀌어 완료 상태를 갱신하지 못했습니다: id={}, messageId={}",
                                event.id(),
                                messageId);
                          }
                        })
                    .then())
        .onErrorResume(error -> recordPublishFailure(event, leaseOwner, error));
  }

  private Mono<Void> recordPublishFailure(
      ClaimedFcmOutboxEvent event, String leaseOwner, Throwable failure) {
    int nextRetryCount = Math.incrementExact(event.retryCount());
    String errorMessage = describe(failure);
    if ("PUBLISHED".equals(event.status()) || nextRetryCount <= maxRetries) {
      int delaySeconds = retryDelaySeconds(nextRetryCount);
      return outboxRepository
          .scheduleRetry(event.id(), leaseOwner, nextRetryCount, delaySeconds, errorMessage)
          .doOnNext(
              rows ->
                  log.warn(
                      "FCM 아웃박스 발행을 재시도 대기 상태로 전환했습니다: id={}, retry={}, delaySeconds={}, updated={}",
                      event.id(),
                      nextRetryCount,
                      delaySeconds,
                      rows))
          .then();
    }

    return transactionOperator
        .transactional(
            outboxRepository
                .markFailed(event.id(), leaseOwner, nextRetryCount, errorMessage)
                .flatMap(
                    rows ->
                        rows == 0
                            ? Mono.just(rows)
                            : deadLetterRepository
                                .save(
                                    "outbox-" + event.id(),
                                    event.eventId(),
                                    event.payload(),
                                    errorMessage,
                                    nextRetryCount)
                                .thenReturn(rows)))
        .doOnNext(
            rows -> {
              if (rows == 0) {
                log.warn("FCM 아웃박스 선점 소유자가 바뀌어 최종 실패를 반영하지 않았습니다: id={}", event.id());
              } else {
                log.error(
                    "FCM 아웃박스 발행 재시도가 모두 실패하여 DLQ에 보관했습니다: id={}, retry={}",
                    event.id(),
                    nextRetryCount);
              }
            })
        .then();
  }

  private int retryDelaySeconds(int retryCount) {
    int exponent = Math.min(30, Math.max(0, retryCount - 1));
    long delay = (long) baseDelaySeconds << exponent;
    return (int) Math.min(maxDelaySeconds, delay);
  }

  private String describe(Throwable failure) {
    String details = failure.getClass().getSimpleName() + ": " + failure.getMessage();
    return details.substring(0, Math.min(MAX_ERROR_LENGTH, details.length()));
  }
}
