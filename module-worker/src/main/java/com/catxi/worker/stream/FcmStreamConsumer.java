package com.catxi.worker.stream;

import com.catxi.notification.contract.FcmNotificationEvent;
import com.catxi.worker.deadletter.DeadLetterRepository;
import com.catxi.worker.notification.DeliveryOutcome;
import com.catxi.worker.notification.NotificationDeliveryService;
import java.net.InetAddress;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.domain.Range.Bound;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.data.redis.stream.StreamReceiver.StreamReceiverOptions;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "fcm.worker.enabled", havingValue = "true")
public class FcmStreamConsumer {

  private static final Logger log = LoggerFactory.getLogger(FcmStreamConsumer.class);

  private final ReactiveRedisConnectionFactory connectionFactory;
  private final ReactiveStringRedisTemplate redisTemplate;
  private final RedisStreamStore streamStore;
  private final NotificationDeliveryService deliveryService;
  private final DeadLetterRepository deadLetterRepository;
  private final JsonMapper objectMapper;
  private final String consumerName = createConsumerName();
  private final Duration pollTimeout;
  private final Duration claimMinIdleTime;
  private final Duration reclaimInterval;
  private final Duration processingTimeout;
  private final Duration retryBaseDelay;
  private final Duration retryMaxDelay;
  private final int readBatchSize;
  private final int reclaimPageSize;
  private final int concurrency;
  private final int maxRetries;

  private Disposable consumeSubscription;
  private Disposable reclaimSubscription;
  private Sinks.Empty<Void> stopSignal = Sinks.empty();
  private CountDownLatch consumeStopped = new CountDownLatch(0);

  public FcmStreamConsumer(
      ReactiveRedisConnectionFactory connectionFactory,
      ReactiveStringRedisTemplate redisTemplate,
      RedisStreamStore streamStore,
      NotificationDeliveryService deliveryService,
      DeadLetterRepository deadLetterRepository,
      JsonMapper objectMapper,
      @Value("${fcm.stream.poll-timeout}") Duration pollTimeout,
      @Value("${fcm.stream.claim-min-idle-time}") Duration claimMinIdleTime,
      @Value("${fcm.stream.reclaim-interval}") Duration reclaimInterval,
      @Value("${fcm.stream.processing-timeout}") Duration processingTimeout,
      @Value("${fcm.stream.retry-base-delay}") Duration retryBaseDelay,
      @Value("${fcm.stream.retry-max-delay}") Duration retryMaxDelay,
      @Value("${fcm.stream.batch-size}") int readBatchSize,
      @Value("${fcm.stream.reclaim-page-size}") int reclaimPageSize,
      @Value("${fcm.stream.concurrency}") int concurrency,
      @Value("${fcm.stream.max-retries}") int maxRetries) {
    this.connectionFactory = connectionFactory;
    this.redisTemplate = redisTemplate;
    this.streamStore = streamStore;
    this.deliveryService = deliveryService;
    this.deadLetterRepository = deadLetterRepository;
    this.objectMapper = objectMapper;
    this.pollTimeout = pollTimeout;
    this.claimMinIdleTime = claimMinIdleTime;
    this.reclaimInterval = reclaimInterval;
    this.processingTimeout = processingTimeout;
    this.retryBaseDelay = retryBaseDelay;
    this.retryMaxDelay = retryMaxDelay;
    this.readBatchSize = Math.max(1, readBatchSize);
    this.reclaimPageSize = Math.max(1, reclaimPageSize);
    this.concurrency = Math.max(1, concurrency);
    this.maxRetries = Math.max(0, maxRetries);
  }

  public synchronized void start() {
    if (consumeSubscription != null && !consumeSubscription.isDisposed()) {
      return;
    }

    StreamReceiverOptions<String, MapRecord<String, String, String>> options =
        StreamReceiverOptions.builder().pollTimeout(pollTimeout).batchSize(readBatchSize).build();
    StreamReceiver<String, MapRecord<String, String, String>> receiver =
        StreamReceiver.create(connectionFactory, options);

    stopSignal = Sinks.empty();
    consumeStopped = new CountDownLatch(1);
    consumeSubscription =
        receiver
            .receive(
                Consumer.from(streamStore.consumerGroup(), consumerName),
                StreamOffset.create(streamStore.streamName(), ReadOffset.lastConsumed()))
            .takeUntilOther(stopSignal.asMono())
            .flatMap(this::processRecord, concurrency, 1)
            .doOnError(error -> log.error("FCM Redis Stream 소비 연결이 끊겼습니다", error))
            .retryWhen(
                Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                    .maxBackoff(Duration.ofSeconds(30)))
            .doFinally(signal -> consumeStopped.countDown())
            .subscribe();

    reclaimSubscription =
        Flux.interval(Duration.ZERO, reclaimInterval)
            .onBackpressureDrop()
            .concatMap(
                ignored ->
                    reclaimPending()
                        .onErrorResume(
                            error -> {
                              log.error("FCM Stream 유휴 pending 회수에 실패했습니다", error);
                              return Flux.empty();
                            }))
            .subscribe();

    log.info("FCM Stream consumer를 시작했습니다: consumer={}", consumerName);
  }

  public synchronized void stop() {
    if (reclaimSubscription != null) {
      reclaimSubscription.dispose();
      reclaimSubscription = null;
    }
    if (consumeSubscription != null) {
      stopSignal.tryEmitEmpty();
      try {
        Duration drainTimeout = processingTimeout.plus(retryMaxDelay).plusSeconds(5);
        if (!consumeStopped.await(drainTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
          log.warn("FCM Stream 처리 종료 대기 시간이 지나 소비 구독을 취소합니다");
          consumeSubscription.dispose();
        }
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        consumeSubscription.dispose();
      }
      consumeSubscription = null;
    }
  }

  private Mono<Void> processRecord(MapRecord<String, String, String> record) {
    String messageId = record.getId().getValue();
    if (streamStore.isBootstrap(record)) {
      return streamStore.acknowledgeAndDelete(messageId);
    }

    String payload = record.getValue().get("payload");
    if (payload == null || payload.isBlank()) {
      return deadLetterAndAcknowledge(messageId, null, payload, "Redis Stream payload가 없습니다", 0);
    }

    FcmNotificationEvent event;
    try {
      String eventPayload = payload;
      if (payload.stripLeading().startsWith("\"")) {
        eventPayload = objectMapper.readValue(payload, String.class);
      }
      event = objectMapper.readValue(eventPayload, FcmNotificationEvent.class);
      validate(event);
    } catch (Exception error) {
      return deadLetterAndAcknowledge(messageId, null, payload, describe(error), 0);
    }

    return deliveryService
        .deliver(event)
        .timeout(processingTimeout)
        .onErrorResume(
            error ->
                recoverDeliveryFailure(messageId, payload, event, error)
                    .then(Mono.<DeliveryOutcome>empty()))
        .flatMap(outcome -> handleOutcome(messageId, payload, event, outcome))
        .onErrorResume(
            error -> {
              log.error(
                  "FCM 이벤트 처리가 완료되지 않아 pending 상태로 둡니다: messageId={}, eventId={}",
                  messageId,
                  event.eventId(),
                  error);
              return Mono.empty();
            });
  }

  private Mono<Void> recoverDeliveryFailure(
      String messageId, String payload, FcmNotificationEvent event, Throwable error) {
    log.warn(
        "FCM 이벤트 처리 중 일시 오류가 발생했습니다: messageId={}, eventId={}, retry={}",
        messageId,
        event.eventId(),
        event.retryCount(),
        error);
    if (event.retryCount() >= maxRetries) {
      return deadLetterAndAcknowledge(
          messageId, event.eventId(), payload, describe(error), event.retryCount());
    }

    FcmNotificationEvent retryEvent = event.withIncrementedRetry();
    return serialize(retryEvent)
        .flatMap(
            retryPayload ->
                Mono.delay(retryDelay(event.retryCount()))
                    .then(streamStore.publishRetryAndAcknowledge(retryPayload, messageId))
                    .then());
  }

  private Mono<Void> handleOutcome(
      String messageId, String payload, FcmNotificationEvent event, DeliveryOutcome outcome) {
    List<Long> retryMemberIds = outcome.retryMemberIds();
    Map<Long, String> terminalFailures = outcome.terminalFailures();

    if (retryMemberIds.isEmpty()) {
      if (terminalFailures.isEmpty()) {
        return streamStore.acknowledgeAndDelete(messageId);
      }
      FcmNotificationEvent failedEvent = withTargets(event, List.copyOf(terminalFailures.keySet()));
      return serialize(failedEvent)
          .flatMap(
              failedPayload ->
                  deadLetterRepository.save(
                      messageId,
                      event.eventId(),
                      failedPayload,
                      terminalFailures.toString(),
                      event.retryCount()))
          .then(streamStore.acknowledgeAndDelete(messageId));
    }

    if (event.retryCount() >= maxRetries) {
      LinkedHashSet<Long> failedIds = new LinkedHashSet<>(retryMemberIds);
      failedIds.addAll(terminalFailures.keySet());
      FcmNotificationEvent failedEvent = withTargets(event, List.copyOf(failedIds));
      String failure =
          terminalFailures.isEmpty() ? "FCM 전송 재시도 횟수를 초과했습니다" : "FCM 전송 실패: " + terminalFailures;
      return serialize(failedEvent)
          .flatMap(
              failedPayload ->
                  deadLetterRepository.save(
                      messageId, event.eventId(), failedPayload, failure, event.retryCount()))
          .then(streamStore.acknowledgeAndDelete(messageId));
    }

    FcmNotificationEvent retryEvent = withTargets(event, retryMemberIds).withIncrementedRetry();
    Mono<Void> persistTerminalFailures =
        terminalFailures.isEmpty()
            ? Mono.empty()
            : serialize(withTargets(event, List.copyOf(terminalFailures.keySet())))
                .flatMap(
                    failedPayload ->
                        deadLetterRepository.save(
                            messageId,
                            event.eventId(),
                            failedPayload,
                            terminalFailures.toString(),
                            event.retryCount()));

    return serialize(retryEvent)
        .flatMap(
            retryPayload ->
                persistTerminalFailures
                    .then(Mono.delay(retryDelay(event.retryCount())))
                    .then(streamStore.publishRetryAndAcknowledge(retryPayload, messageId))
                    .then());
  }

  private Mono<Void> deadLetterAndAcknowledge(
      String messageId, String eventId, String payload, String error, int retryCount) {
    return deadLetterRepository
        .save(messageId, eventId, payload, error, retryCount)
        .then(streamStore.acknowledgeAndDelete(messageId));
  }

  private Mono<String> serialize(FcmNotificationEvent event) {
    return Mono.fromCallable(() -> objectMapper.writeValueAsString(event));
  }

  private FcmNotificationEvent withTargets(FcmNotificationEvent event, List<Long> memberIds) {
    return new FcmNotificationEvent(
        event.eventId(),
        event.type(),
        memberIds,
        event.title(),
        event.body(),
        event.data(),
        event.createdAt(),
        event.retryCount());
  }

  private void validate(FcmNotificationEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("FCM 이벤트가 비어 있습니다");
    }
    if (event.eventId() == null || event.eventId().isBlank()) {
      throw new IllegalArgumentException("FCM eventId가 비어 있습니다");
    }
    if (event.type() == null) {
      throw new IllegalArgumentException("FCM 이벤트 유형이 비어 있습니다");
    }
    if (event.targetMemberIds() == null
        || event.targetMemberIds().stream().anyMatch(id -> id == null || id <= 0)) {
      throw new IllegalArgumentException("FCM 대상 회원 ID가 올바르지 않습니다");
    }
    if (event.retryCount() < 0) {
      throw new IllegalArgumentException("FCM 재시도 횟수가 음수입니다");
    }
    if (event.title() == null || event.body() == null || event.data() == null) {
      throw new IllegalArgumentException("FCM 알림 제목, 내용, 데이터는 비어 있을 수 없습니다");
    }
    String roomId = event.data().get("roomId");
    if (roomId == null || roomId.isBlank()) {
      throw new IllegalArgumentException("FCM 이벤트에 roomId가 없습니다");
    }
    try {
      if (Long.parseLong(roomId) <= 0) {
        throw new IllegalArgumentException("FCM 이벤트 roomId가 올바르지 않습니다");
      }
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException("FCM 이벤트 roomId가 올바르지 않습니다", error);
    }
  }

  private Flux<Void> reclaimPending() {
    return redisTemplate
        .opsForStream()
        .pending(streamStore.streamName(), streamStore.consumerGroup())
        .flatMapMany(
            summary -> {
              if (summary.getTotalPendingMessages() == 0) {
                return Flux.empty();
              }
              return claimPendingPages(null);
            })
        .concatMap(this::processRecord);
  }

  private Flux<MapRecord<String, String, String>> claimPendingPages(String cursor) {
    Range<String> range =
        cursor == null ? Range.unbounded() : Range.rightUnbounded(Bound.exclusive(cursor));

    return redisTemplate
        .opsForStream()
        .pending(streamStore.streamName(), streamStore.consumerGroup(), range, reclaimPageSize)
        .flatMapMany(this::claimPage);
  }

  private Flux<MapRecord<String, String, String>> claimPage(PendingMessages pending) {
    List<PendingMessage> messages = pending.stream().toList();
    if (messages.isEmpty()) {
      return Flux.empty();
    }

    String lastId = messages.get(messages.size() - 1).getIdAsString();
    RecordId[] reclaimableIds =
        messages.stream()
            .filter(
                message ->
                    message.getElapsedTimeSinceLastDelivery().compareTo(claimMinIdleTime) >= 0)
            .map(PendingMessage::getId)
            .toArray(RecordId[]::new);
    Flux<MapRecord<String, String, String>> claimed =
        reclaimableIds.length == 0
            ? Flux.empty()
            : redisTemplate
                .<String, String>opsForStream()
                .claim(
                    streamStore.streamName(),
                    streamStore.consumerGroup(),
                    consumerName,
                    XClaimOptions.minIdle(claimMinIdleTime).ids(reclaimableIds));
    Flux<MapRecord<String, String, String>> nextPage =
        messages.size() < reclaimPageSize
            ? Flux.empty()
            : Flux.defer(() -> claimPendingPages(lastId));
    return claimed.concatWith(nextPage);
  }

  private Duration retryDelay(int retryCount) {
    int exponent = Math.min(30, Math.max(0, retryCount));
    long delaySeconds = retryBaseDelay.toSeconds() * (1L << exponent);
    return Duration.ofSeconds(Math.min(retryMaxDelay.toSeconds(), delaySeconds));
  }

  private String describe(Throwable error) {
    String message = error.getClass().getSimpleName() + ": " + error.getMessage();
    return message.length() > 4000 ? message.substring(0, 4000) : message;
  }

  private static String createConsumerName() {
    String host;
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception ignored) {
      host = "worker";
    }
    return host + "-" + UUID.randomUUID();
  }
}
