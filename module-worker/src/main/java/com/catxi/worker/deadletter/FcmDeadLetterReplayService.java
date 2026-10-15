package com.catxi.worker.deadletter;

import com.catxi.notification.contract.FcmNotificationEvent;
import com.catxi.worker.deadletter.DeadLetterRepository.DeadLetterRecord;
import com.catxi.worker.outbox.FcmOutboxRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Service
public class FcmDeadLetterReplayService {

  private final DeadLetterRepository deadLetterRepository;
  private final FcmOutboxRepository outboxRepository;
  private final TransactionalOperator transactionOperator;
  private final JsonMapper objectMapper;

  public FcmDeadLetterReplayService(
      DeadLetterRepository deadLetterRepository,
      FcmOutboxRepository outboxRepository,
      TransactionalOperator transactionOperator,
      JsonMapper objectMapper) {
    this.deadLetterRepository = deadLetterRepository;
    this.outboxRepository = outboxRepository;
    this.transactionOperator = transactionOperator;
    this.objectMapper = objectMapper;
  }

  public Mono<ReplayResult> replay(long deadLetterId) {
    if (deadLetterId <= 0) {
      return Mono.error(new IllegalArgumentException("DLQ ID는 양수여야 합니다"));
    }

    return transactionOperator.transactional(
        deadLetterRepository
            .findForReplay(deadLetterId)
            .switchIfEmpty(
                Mono.error(new IllegalArgumentException("FCM DLQ 기록을 찾을 수 없습니다: " + deadLetterId)))
            .flatMap(this::enqueueReplay));
  }

  private Mono<ReplayResult> enqueueReplay(DeadLetterRecord deadLetter) {
    if (deadLetter.replayEventId() != null) {
      return outboxRepository
          .existsByEventId(deadLetter.replayEventId())
          .flatMap(
              exists ->
                  exists
                      ? Mono.just(
                          new ReplayResult(deadLetter.id(), deadLetter.replayEventId(), true))
                      : Mono.error(
                          new IllegalStateException("재처리 표시가 있지만 아웃박스 행이 없습니다. DB 상태를 먼저 확인하세요")));
    }

    return Mono.fromCallable(() -> parseOriginalEvent(deadLetter))
        .flatMap(
            originalEvent -> {
              String replayEventId = UUID.randomUUID().toString();
              FcmNotificationEvent replayEvent = originalEvent.forReplay(replayEventId);
              return Mono.fromCallable(() -> objectMapper.writeValueAsString(replayEvent))
                  .flatMap(
                      payload ->
                          deadLetterRepository
                              .markReplayRequested(deadLetter.id(), replayEventId)
                              .flatMap(
                                  updated ->
                                      updated == 1
                                          ? outboxRepository
                                              .insertReplayEvent(
                                                  replayEventId, replayEvent.type().name(), payload)
                                              .thenReturn(
                                                  new ReplayResult(
                                                      deadLetter.id(), replayEventId, false))
                                          : Mono.error(
                                              new IllegalStateException(
                                                  "FCM DLQ 재처리 요청을 선점하지 못했습니다"))));
            });
  }

  private FcmNotificationEvent parseOriginalEvent(DeadLetterRecord deadLetter) {
    if (deadLetter.eventId() == null || deadLetter.eventId().isBlank()) {
      throw new IllegalStateException("이벤트 ID가 없는 FCM DLQ 기록은 재처리할 수 없습니다");
    }
    if (deadLetter.payload() == null || deadLetter.payload().isBlank()) {
      throw new IllegalStateException("페이로드가 없는 FCM DLQ 기록은 재처리할 수 없습니다");
    }

    try {
      String payload = deadLetter.payload();
      if (payload.stripLeading().startsWith("\"")) {
        payload = objectMapper.readValue(payload, String.class);
      }
      FcmNotificationEvent event = objectMapper.readValue(payload, FcmNotificationEvent.class);
      if (event == null || event.type() == null || event.targetMemberIds().isEmpty()) {
        throw new IllegalStateException("알림 유형 또는 대상이 없는 DLQ 기록은 재처리할 수 없습니다");
      }
      if (!deadLetter.eventId().equals(event.eventId())) {
        throw new IllegalStateException("DLQ 이벤트 ID와 페이로드 이벤트 ID가 일치하지 않습니다");
      }
      return event;
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("FCM DLQ 페이로드를 읽을 수 없습니다", exception);
    }
  }

  public record ReplayResult(long deadLetterId, String replayEventId, boolean alreadyRequested) {}
}
