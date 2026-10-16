package com.catxi.worker.notification.delivery;

import com.catxi.worker.notification.event.FcmNotificationEvent;
import com.catxi.worker.notification.event.FcmNotificationEvent.NotificationType;
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
                        targets -> {
                          List<FcmTarget> registeredTargets = new ArrayList<>();
                          Map<Long, String> missingRegistrations = new LinkedHashMap<>();
                          for (FcmRecipientRepository.Recipient target : targets) {
                            if (target.fid() != null && !target.fid().isBlank()) {
                              registeredTargets.add(
                                  new FcmTarget(target.memberId(), target.fid(), TargetType.FID));
                            } else if (target.token() != null && !target.token().isBlank()) {
                              registeredTargets.add(
                                  new FcmTarget(
                                      target.memberId(), target.token(), TargetType.TOKEN));
                            } else {
                              missingRegistrations.put(
                                  target.memberId(), "missing_fcm_registration");
                            }
                          }

                          DeliveryOutcome missingOutcome =
                              new DeliveryOutcome(List.of(), missingRegistrations);
                          if (registeredTargets.isEmpty()) {
                            return Mono.just(missingOutcome);
                          }
                          return sendBatch(event, registeredTargets)
                              .map(outcome -> combine(List.of(missingOutcome, outcome)));
                        }))
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

  private Mono<DeliveryOutcome> sendBatch(FcmNotificationEvent event, List<FcmTarget> targets) {
    List<FcmTarget> tokenTargets =
        targets.stream().filter(target -> target.type() == TargetType.TOKEN).toList();
    List<FcmTarget> fidTargets =
        targets.stream().filter(target -> target.type() == TargetType.FID).toList();
    return Flux.concat(
            sendTargetBatch(event, tokenTargets, TargetType.TOKEN),
            sendTargetBatch(event, fidTargets, TargetType.FID))
        .collectList()
        .map(this::combine);
  }

  private Mono<DeliveryOutcome> sendTargetBatch(
      FcmNotificationEvent event, List<FcmTarget> targets, TargetType targetType) {
    if (targets.isEmpty()) {
      return Mono.just(DeliveryOutcome.completed());
    }
    Map<String, String> data = new LinkedHashMap<>(event.data());
    data.put("eventId", event.eventId());
    data.put("title", event.title());
    data.put("body", event.body());
    MulticastMessage.Builder messageBuilder = MulticastMessage.builder();
    if (targetType == TargetType.FID) {
      messageBuilder.addAllFids(targets.stream().map(FcmTarget::identifier).toList());
    } else {
      messageBuilder.addAllTokens(targets.stream().map(FcmTarget::identifier).toList());
    }
    MulticastMessage message = messageBuilder.putAllData(data).build();

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

  private Mono<DeliveryOutcome> classifyResponse(List<FcmTarget> targets, BatchResponse response) {
    List<Long> retryMemberIds = new ArrayList<>();
    Map<Long, String> terminalFailures = new LinkedHashMap<>();
    List<FcmTarget> expiredTargets = new ArrayList<>();
    List<SendResponse> responses = response.getResponses();

    if (responses.size() != targets.size()) {
      return Mono.just(retryAll(targets, new IllegalStateException("FCM 응답 수가 요청 대상 수와 다릅니다")));
    }

    for (int index = 0; index < responses.size(); index++) {
      SendResponse sendResponse = responses.get(index);
      if (sendResponse.isSuccessful()) {
        continue;
      }

      FcmTarget target = targets.get(index);
      FirebaseMessagingException exception = sendResponse.getException();
      MessagingErrorCode code = exception == null ? null : exception.getMessagingErrorCode();
      if (code == MessagingErrorCode.UNREGISTERED) {
        expiredTargets.add(target);
      } else if (code == MessagingErrorCode.INVALID_ARGUMENT) {
        terminalFailures.put(target.memberId(), describe(exception));
      } else {
        retryMemberIds.add(target.memberId());
      }
    }

    return Flux.fromIterable(expiredTargets)
        .concatMap(
            target -> {
              Mono<Boolean> clearResult =
                  target.type() == TargetType.FID
                      ? recipientRepository.clearExpiredFid(target.memberId(), target.identifier())
                      : recipientRepository.clearExpiredToken(
                          target.memberId(), target.identifier());
              return clearResult
                  .map(cleared -> new CleanupResult(target, cleared))
                  .onErrorResume(error -> Mono.just(new CleanupResult(target, false)));
            })
        .collectList()
        .map(
            cleanups -> {
              for (CleanupResult cleanup : cleanups) {
                if (!cleanup.cleared()) {
                  retryMemberIds.add(cleanup.target().memberId());
                }
              }
              return new DeliveryOutcome(distinct(retryMemberIds), terminalFailures);
            });
  }

  private DeliveryOutcome retryAll(List<FcmTarget> targets, Throwable error) {
    log.warn(
        "FCM 배치 전송을 재시도합니다: targetCount={}, errorType={}",
        targets.size(),
        error.getClass().getSimpleName());
    return new DeliveryOutcome(
        distinct(targets.stream().map(FcmTarget::memberId).toList()), Map.of());
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

  private enum TargetType {
    TOKEN,
    FID
  }

  private record FcmTarget(long memberId, String identifier, TargetType type) {}

  private record CleanupResult(FcmTarget target, boolean cleared) {}
}
