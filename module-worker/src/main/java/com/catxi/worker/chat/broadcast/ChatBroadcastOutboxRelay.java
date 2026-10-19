package com.catxi.worker.chat.broadcast;

import com.catxi.worker.chat.broadcast.ChatBroadcastOutboxRepository.ClaimedEvent;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(name = "chat.broadcast.worker.enabled", havingValue = "true")
public class ChatBroadcastOutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(ChatBroadcastOutboxRelay.class);
  private static final String CHAT_CHANNEL = "chat";
  private static final int MAX_ERROR_LENGTH = 4000;

  private final ChatBroadcastOutboxRepository outboxRepository;
  private final ReactiveStringRedisTemplate redisTemplate;
  private final long baseDelaySeconds;
  private final long maxDelaySeconds;

  public ChatBroadcastOutboxRelay(
      ChatBroadcastOutboxRepository outboxRepository,
      ReactiveStringRedisTemplate redisTemplate,
      @Value("${chat.broadcast.outbox.retry-base-delay}") Duration baseDelay,
      @Value("${chat.broadcast.outbox.retry-max-delay}") Duration maxDelay) {
    this.outboxRepository = outboxRepository;
    this.redisTemplate = redisTemplate;
    this.baseDelaySeconds = Math.max(1, baseDelay.toSeconds());
    this.maxDelaySeconds = Math.max(baseDelaySeconds, maxDelay.toSeconds());
  }

  public Mono<Void> publish(ClaimedEvent event, String leaseOwner) {
    return redisTemplate
        .convertAndSend(CHAT_CHANNEL, event.payload())
        .switchIfEmpty(Mono.error(new IllegalStateException("Redis 발행 결과를 받지 못했습니다")))
        .thenReturn(true)
        .onErrorResume(
            failure -> {
              log.warn("채팅 브로드캐스트 Redis 발행 실패, 재시도합니다. messageId={}", event.messageId(), failure);
              return recordRetry(event, leaseOwner, failure).thenReturn(false);
            })
        .flatMap(
            published -> {
              if (!published) {
                return Mono.empty();
              }
              return outboxRepository
                  .markSent(event.id(), leaseOwner)
                  .flatMap(
                      updated ->
                          updated == 1
                              ? Mono.<Void>empty()
                              : Mono.error(
                                  new IllegalStateException("채팅 브로드캐스트 완료 상태를 기록하지 못했습니다")))
                  .onErrorResume(
                      failure -> {
                        log.error(
                            "Redis 발행 후 완료 상태 기록에 실패했습니다. 같은 messageId로 재전송될 수 있습니다. messageId={}",
                            event.messageId(),
                            failure);
                        return recordRetry(event, leaseOwner, failure);
                      });
            });
  }

  private Mono<Void> recordRetry(ClaimedEvent event, String leaseOwner, Throwable failure) {
    long delaySeconds = retryDelaySeconds(event.retryCount());
    return outboxRepository
        .scheduleRetry(event, leaseOwner, delaySeconds, errorMessage(failure))
        .doOnNext(
            updated -> {
              if (updated == 1) {
                log.warn(
                    "채팅 브로드캐스트 재시도를 예약했습니다. messageId={}, retryCount={}, delaySeconds={}",
                    event.messageId(),
                    event.retryCount() == Long.MAX_VALUE ? Long.MAX_VALUE : event.retryCount() + 1,
                    delaySeconds);
              } else {
                log.error(
                    "채팅 브로드캐스트 재시도 예약을 반영하지 못했습니다. 임대 만료 후 다시 처리합니다. messageId={}",
                    event.messageId());
              }
            })
        .then();
  }

  private long retryDelaySeconds(long retryCount) {
    long delaySeconds = baseDelaySeconds;
    for (int retry = 0; retry < Math.min(Math.max(0, retryCount), Long.SIZE); retry++) {
      if (delaySeconds >= maxDelaySeconds) {
        break;
      }
      delaySeconds =
          Math.min(
              maxDelaySeconds,
              delaySeconds > maxDelaySeconds / 2 ? maxDelaySeconds : delaySeconds * 2);
    }
    return delaySeconds;
  }

  private String errorMessage(Throwable failure) {
    String details =
        failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
    return details.substring(0, Math.min(MAX_ERROR_LENGTH, details.length()));
  }
}
