package com.catxi.worker.chat.broadcast;

import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@ConditionalOnProperty(name = "chat.broadcast.worker.enabled", havingValue = "true")
public class ChatBroadcastOutboxPoller {

  private static final int MAX_CLEANUP_BATCHES_PER_RUN = 10;

  private final ChatBroadcastOutboxRepository outboxRepository;
  private final ChatBroadcastOutboxRelay outboxRelay;
  private final String leaseOwner = UUID.randomUUID().toString();
  private final Duration pollInterval;
  private final long sentRetentionSeconds;
  private final int cleanupBatchSize;
  private final int publishConcurrency;
  private Disposable pollingSubscription;
  private Disposable cleanupSubscription;

  public ChatBroadcastOutboxPoller(
      ChatBroadcastOutboxRepository outboxRepository,
      ChatBroadcastOutboxRelay outboxRelay,
      @Value("${chat.broadcast.outbox.poll-interval}") Duration pollInterval,
      @Value("${chat.broadcast.outbox.sent-retention}") Duration sentRetention,
      @Value("${chat.broadcast.outbox.cleanup-batch-size}") int cleanupBatchSize,
      @Value("${chat.broadcast.outbox.publish-concurrency}") int publishConcurrency) {
    this.outboxRepository = outboxRepository;
    this.outboxRelay = outboxRelay;
    this.pollInterval = pollInterval;
    this.sentRetentionSeconds = Math.max(1, sentRetention.toSeconds());
    this.cleanupBatchSize = Math.max(1, cleanupBatchSize);
    this.publishConcurrency = Math.max(1, publishConcurrency);
  }

  @jakarta.annotation.PostConstruct
  void start() {
    pollingSubscription =
        Flux.interval(Duration.ZERO, pollInterval)
            .onBackpressureDrop()
            .concatMap(tick -> pollBatch().onErrorResume(this::logPollFailure), 1)
            .subscribe();
    cleanupSubscription =
        Flux.interval(Duration.ofHours(1), Duration.ofHours(1))
            .onBackpressureDrop()
            .concatMap(tick -> cleanupSentEvents().onErrorResume(this::logCleanupFailure), 1)
            .subscribe();
  }

  private Mono<Void> pollBatch() {
    return outboxRepository
        .claimDue(leaseOwner, publishConcurrency)
        .flatMapMany(Flux::fromIterable)
        .flatMap(
            event ->
                outboxRelay
                    .publish(event, leaseOwner)
                    .onErrorResume(
                        failure -> {
                          log.error(
                              "채팅 브로드캐스트 처리에 실패했습니다. 임대 만료 후 다시 처리합니다. messageId={}",
                              event.messageId(),
                              failure);
                          return Mono.empty();
                        }),
            publishConcurrency)
        .then();
  }

  private Mono<Void> cleanupSentEvents() {
    return deleteSentBatches(MAX_CLEANUP_BATCHES_PER_RUN);
  }

  private Mono<Void> deleteSentBatches(int remainingBatches) {
    if (remainingBatches == 0) {
      return Mono.empty();
    }
    return outboxRepository
        .deleteSentBatch(sentRetentionSeconds, cleanupBatchSize)
        .flatMap(
            deleted ->
                deleted < cleanupBatchSize
                    ? Mono.empty()
                    : deleteSentBatches(remainingBatches - 1));
  }

  private Mono<Void> logPollFailure(Throwable failure) {
    log.error("채팅 브로드캐스트 워커 조회에 실패했습니다", failure);
    return Mono.empty();
  }

  private Mono<Void> logCleanupFailure(Throwable failure) {
    log.error("완료된 채팅 브로드캐스트 이력 정리에 실패했습니다", failure);
    return Mono.empty();
  }

  @jakarta.annotation.PreDestroy
  void stop() {
    if (pollingSubscription != null) {
      pollingSubscription.dispose();
    }
    if (cleanupSubscription != null) {
      cleanupSubscription.dispose();
    }
  }
}
