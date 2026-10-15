package com.catxi.worker.stream;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "fcm.worker.enabled", havingValue = "true")
public class RedisStreamStore {

  private static final String BOOTSTRAP_FIELD = "_bootstrap";
  private static final String IDEMPOTENCY_KEY_PREFIX = "fcm:published:";
  private static final String RETRY_IDEMPOTENCY_KEY_PREFIX = "fcm:retry:";
  private static final String OUTBOX_PUBLISH_SCRIPT_SOURCE =
      """
            local existing = redis.call('GET', KEYS[2])
            if existing then
                return existing
            end
            local messageId = redis.call('XADD', KEYS[1], '*', 'payload', ARGV[1])
            redis.call('SET', KEYS[2], messageId, 'EX', ARGV[2])
            return messageId
            """;
  private static final RedisScript<String> OUTBOX_PUBLISH_SCRIPT =
      new DefaultRedisScript<>(OUTBOX_PUBLISH_SCRIPT_SOURCE, String.class);
  private static final RedisScript<String> RETRY_AND_ACK_SCRIPT =
      new DefaultRedisScript<>(
          """
            local retryId = redis.call('GET', KEYS[2])
            if not retryId then
                retryId = redis.call('XADD', KEYS[1], '*', 'payload', ARGV[1])
                redis.call('SET', KEYS[2], retryId, 'EX', ARGV[4])
            end
            local acknowledged = redis.call('XACK', KEYS[1], ARGV[2], ARGV[3])
            if acknowledged == 1 then
                redis.call('XDEL', KEYS[1], ARGV[3])
            end
            return retryId
            """,
          String.class);
  private static final RedisScript<Long> ACK_AND_DELETE_SCRIPT =
      new DefaultRedisScript<>(
          """
            local acknowledged = redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
            if acknowledged == 1 then
                return redis.call('XDEL', KEYS[1], ARGV[2])
            end
            return 0
            """,
          Long.class);

  private final ReactiveStringRedisTemplate redisTemplate;
  private final String streamName;
  private final String consumerGroup;
  private final long idempotencyTtlSeconds;

  public RedisStreamStore(
      ReactiveStringRedisTemplate redisTemplate,
      @Value("${fcm.stream.name}") String streamName,
      @Value("${fcm.stream.group}") String consumerGroup,
      @Value("${fcm.stream.idempotency-ttl}") java.time.Duration idempotencyTtl) {
    this.redisTemplate = redisTemplate;
    this.streamName = streamName;
    this.consumerGroup = consumerGroup;
    this.idempotencyTtlSeconds = Math.max(1, idempotencyTtl.toSeconds());
  }

  public String streamName() {
    return streamName;
  }

  public String consumerGroup() {
    return consumerGroup;
  }

  public Mono<Long> size() {
    return redisTemplate.opsForStream().size(streamName).defaultIfEmpty(0L);
  }

  public Mono<Long> pendingCount() {
    return redisTemplate
        .opsForStream()
        .pending(streamName, consumerGroup)
        .map(PendingMessagesSummary::getTotalPendingMessages);
  }

  public Mono<Void> ensureConsumerGroup() {
    MapRecord<String, String, String> bootstrap =
        MapRecord.create(streamName, Map.of(BOOTSTRAP_FIELD, "true"));

    return redisTemplate
        .opsForStream()
        .add(bootstrap)
        .flatMap(
            recordId ->
                redisTemplate
                    .opsForStream()
                    .createGroup(streamName, ReadOffset.from("0-0"), consumerGroup)
                    .onErrorResume(
                        error -> isGroupAlreadyExists(error) ? Mono.empty() : Mono.error(error))
                    .then(redisTemplate.opsForStream().delete(streamName, recordId))
                    .then());
  }

  public boolean isBootstrap(MapRecord<String, String, String> record) {
    return "true".equals(record.getValue().get(BOOTSTRAP_FIELD));
  }

  public Mono<String> publishOutbox(String eventId, String payload) {
    return redisTemplate
        .execute(
            OUTBOX_PUBLISH_SCRIPT,
            List.of(streamName, IDEMPOTENCY_KEY_PREFIX + eventId),
            payload,
            Long.toString(idempotencyTtlSeconds))
        .single()
        .switchIfEmpty(Mono.error(new IllegalStateException("Redis가 스트림 메시지 ID를 반환하지 않았습니다")));
  }

  public Mono<String> publishRetryAndAcknowledge(String payload, String sourceMessageId) {
    return redisTemplate
        .execute(
            RETRY_AND_ACK_SCRIPT,
            List.of(streamName, RETRY_IDEMPOTENCY_KEY_PREFIX + sourceMessageId),
            payload,
            consumerGroup,
            sourceMessageId,
            Long.toString(idempotencyTtlSeconds))
        .single()
        .switchIfEmpty(Mono.error(new IllegalStateException("Redis가 재시도 메시지 ID를 반환하지 않았습니다")));
  }

  public Mono<Void> acknowledgeAndDelete(String messageId) {
    return redisTemplate
        .execute(ACK_AND_DELETE_SCRIPT, List.of(streamName), consumerGroup, messageId)
        .single()
        .then();
  }

  private boolean isGroupAlreadyExists(Throwable error) {
    Throwable current = error;
    while (current != null) {
      String message = current.getMessage();
      if (message != null && message.contains("BUSYGROUP")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
