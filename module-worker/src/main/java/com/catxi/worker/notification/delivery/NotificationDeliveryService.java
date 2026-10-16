package com.catxi.worker.notification.delivery;

import com.catxi.contract.chat.ChatPresenceKeys;
import com.catxi.contract.notification.FcmNotificationEvent;
import com.catxi.contract.notification.FcmNotificationEvent.NotificationType;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(name = "fcm.worker.enabled", havingValue = "true")
public class NotificationDeliveryService {

  private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);
  private static final int FCM_BATCH_LIMIT = 500;
  private static final int REDIS_LOOKUP_CONCURRENCY = 2;
  private final FcmRecipientRepository recipientRepository;
  private final ReactiveStringRedisTemplate redisTemplate;
  private final FirebaseMessaging firebaseMessaging;

  public NotificationDeliveryService(
      FcmRecipientRepository recipientRepository,
      ReactiveStringRedisTemplate redisTemplate,
      FirebaseMessaging firebaseMessaging) {
    this.recipientRepository = recipientRepository;
    this.redisTemplate = redisTemplate;
    this.firebaseMessaging = firebaseMessaging;
  }

  public Mono<DeliveryOutcome> deliver(FcmNotificationEvent event) {
    if (event.targetMemberIds().isEmpty()) {
      return Mono.just(DeliveryOutcome.completed());
    }

    String roomId = event.data().get("roomId");
    if (roomId == null || roomId.isBlank()) {
      return Mono.error(new IllegalArgumentException("알림 이벤트에 roomId가 없습니다"));
    }

    return Flux.fromIterable(event.targetMemberIds())
        .distinct()
        .buffer(FCM_BATCH_LIMIT)
        .concatMap(
            memberIds ->
                recipientRepository
                    .findActive(memberIds)
                    .flatMap(
                        target ->
                            event.type() == NotificationType.MATCH_COMPLETED
                                ? Mono.just(target)
                                : isInactiveInRoom(target.memberId(), roomId)
                                    .filter(inactive -> inactive)
                                    .map(ignored -> target),
                        REDIS_LOOKUP_CONCURRENCY)
                    .collectList()
                    .flatMap(
                        targets ->
                            targets.isEmpty()
                                ? Mono.just(DeliveryOutcome.completed())
                                : sendBatch(
                                    event,
                                    targets.stream()
                                        .map(
                                            target ->
                                                new MemberToken(target.memberId(), target.token()))
                                        .toList())))
        .collectList()
        .map(this::combine);
  }

  private Mono<Boolean> isInactiveInRoom(long memberId, String roomId) {
    String member = Long.toString(memberId);
    return redisTemplate
        .opsForValue()
        .get(ChatPresenceKeys.activeMarker(roomId, member))
        .hasElement()
        .flatMap(
            legacyMarkerExists ->
                legacyMarkerExists
                    ? Mono.just(false)
                    : redisTemplate
                        .opsForZSet()
                        .count(
                            ChatPresenceKeys.sessionRegistry(roomId, member),
                            Range.closed((double) System.currentTimeMillis(), Double.MAX_VALUE))
                        .map(activeSessions -> activeSessions == 0L))
        .onErrorResume(
            error -> {
              log.warn(
                  "채팅방 활성 상태 조회가 실패해 푸시 알림을 발송합니다: roomId={}, errorType={}",
                  roomId,
                  error.getClass().getSimpleName());
              return Mono.just(true);
            });
  }

  private Mono<DeliveryOutcome> sendBatch(FcmNotificationEvent event, List<MemberToken> targets) {
    Map<String, String> data = new LinkedHashMap<>(event.data());
    data.put("eventId", event.eventId());
    data.put("title", event.title());
    data.put("body", event.body());
    MulticastMessage message =
        MulticastMessage.builder()
            .addAllTokens(targets.stream().map(MemberToken::token).toList())
            .putAllData(data)
            .build();

    return sendAsync(message)
        .flatMap(response -> classifyResponse(targets, response))
        .onErrorResume(error -> Mono.just(retryAll(targets, error)));
  }

  private Mono<BatchResponse> sendAsync(MulticastMessage message) {
    return Mono.<BatchResponse>create(
        sink -> {
          ApiFuture<BatchResponse> future;
          try {
            future = firebaseMessaging.sendEachForMulticastAsync(message);
          } catch (Exception error) {
            sink.error(error);
            return;
          }
          sink.onCancel(() -> future.cancel(false));

          ApiFutures.addCallback(
              future,
              new ApiFutureCallback<>() {
                @Override
                public void onFailure(Throwable error) {
                  sink.error(error);
                }

                @Override
                public void onSuccess(BatchResponse response) {
                  sink.success(response);
                }
              },
              Runnable::run);
        });
  }

  private Mono<DeliveryOutcome> classifyResponse(
      List<MemberToken> targets, BatchResponse response) {
    List<Long> retryMemberIds = new ArrayList<>();
    Map<Long, String> terminalFailures = new LinkedHashMap<>();
    List<MemberToken> expiredTokens = new ArrayList<>();
    List<SendResponse> responses = response.getResponses();

    if (responses.size() != targets.size()) {
      return Mono.just(retryAll(targets, new IllegalStateException("FCM 응답 수가 요청 토큰 수와 다릅니다")));
    }

    for (int index = 0; index < responses.size(); index++) {
      SendResponse sendResponse = responses.get(index);
      if (sendResponse.isSuccessful()) {
        continue;
      }

      MemberToken target = targets.get(index);
      FirebaseMessagingException exception = sendResponse.getException();
      MessagingErrorCode code = exception == null ? null : exception.getMessagingErrorCode();
      if (code == MessagingErrorCode.UNREGISTERED) {
        expiredTokens.add(target);
      } else if (code == MessagingErrorCode.INVALID_ARGUMENT) {
        terminalFailures.put(target.memberId(), describe(exception));
      } else {
        retryMemberIds.add(target.memberId());
      }
    }

    return Flux.fromIterable(expiredTokens)
        .concatMap(
            target ->
                recipientRepository
                    .clearExpiredToken(target.memberId(), target.token())
                    .map(cleared -> new TokenCleanupResult(target, cleared))
                    .onErrorResume(error -> Mono.just(new TokenCleanupResult(target, false))))
        .collectList()
        .map(
            cleanups -> {
              for (TokenCleanupResult cleanup : cleanups) {
                if (!cleanup.cleared()) {
                  retryMemberIds.add(cleanup.target().memberId());
                }
              }
              return new DeliveryOutcome(distinct(retryMemberIds), terminalFailures);
            });
  }

  private DeliveryOutcome retryAll(List<MemberToken> targets, Throwable error) {
    log.warn(
        "FCM 배치 전송을 재시도합니다: targetCount={}, errorType={}",
        targets.size(),
        error.getClass().getSimpleName());
    return new DeliveryOutcome(
        distinct(targets.stream().map(MemberToken::memberId).toList()), Map.of());
  }

  private DeliveryOutcome combine(List<DeliveryOutcome> outcomes) {
    Set<Long> retryIds = new LinkedHashSet<>();
    Map<Long, String> failures = new LinkedHashMap<>();
    for (DeliveryOutcome outcome : outcomes) {
      retryIds.addAll(outcome.retryMemberIds());
      failures.putAll(outcome.terminalFailures());
    }
    return new DeliveryOutcome(List.copyOf(retryIds), failures);
  }

  private List<Long> distinct(List<Long> memberIds) {
    return List.copyOf(new LinkedHashSet<>(memberIds));
  }

  private String describe(Throwable error) {
    if (error == null) {
      return "FCM 토큰 전송 오류";
    }
    String message = error.getClass().getSimpleName() + ": " + error.getMessage();
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }

  private record MemberToken(long memberId, String token) {}

  private record TokenCleanupResult(MemberToken target, boolean cleared) {}
}
