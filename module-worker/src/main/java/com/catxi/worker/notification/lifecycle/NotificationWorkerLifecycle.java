package com.catxi.worker.notification.lifecycle;

import com.catxi.worker.notification.metrics.NotificationWorkerMetrics;
import com.catxi.worker.notification.outbox.FcmOutboxPoller;
import com.catxi.worker.notification.redis.FcmStreamConsumer;
import com.catxi.worker.notification.redis.RedisStreamStore;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.util.retry.Retry;

@Component("notificationWorker")
public class NotificationWorkerLifecycle implements HealthIndicator {

  private static final Logger log = LoggerFactory.getLogger(NotificationWorkerLifecycle.class);

  private final ObjectProvider<RedisStreamStore> streamStoreProvider;
  private final ObjectProvider<FcmOutboxPoller> outboxPollerProvider;
  private final ObjectProvider<FcmStreamConsumer> streamConsumerProvider;
  private final ObjectProvider<NotificationWorkerMetrics> queueMetricsProvider;
  private final AtomicBoolean ready;
  private Disposable startupSubscription;
  private boolean started;

  public NotificationWorkerLifecycle(
      ObjectProvider<RedisStreamStore> streamStoreProvider,
      ObjectProvider<FcmOutboxPoller> outboxPollerProvider,
      ObjectProvider<FcmStreamConsumer> streamConsumerProvider,
      ObjectProvider<NotificationWorkerMetrics> queueMetricsProvider,
      @Value("${fcm.notifications.enabled:false}") boolean notificationsEnabled) {
    this.streamStoreProvider = streamStoreProvider;
    this.outboxPollerProvider = outboxPollerProvider;
    this.streamConsumerProvider = streamConsumerProvider;
    this.queueMetricsProvider = queueMetricsProvider;
    this.ready = new AtomicBoolean(!notificationsEnabled);
  }

  @EventListener(ApplicationReadyEvent.class)
  public synchronized void start() {
    if (started) {
      return;
    }
    started = true;

    RedisStreamStore streamStore = streamStoreProvider.getIfAvailable();
    if (streamStore == null) {
      log.info("푸시 알림 처리가 꺼져 있어 워커를 시작하지 않습니다");
      return;
    }

    NotificationWorkerMetrics queueMetrics = queueMetricsProvider.getIfAvailable();
    if (queueMetrics != null) {
      queueMetrics.start();
    }
    startupSubscription =
        streamStore
            .ensureConsumerGroup()
            .retryWhen(
                Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                    .maxBackoff(Duration.ofSeconds(30)))
            .doOnSuccess(
                ignored -> {
                  FcmStreamConsumer streamConsumer = streamConsumerProvider.getIfAvailable();
                  FcmOutboxPoller outboxPoller = outboxPollerProvider.getIfAvailable();
                  if (streamConsumer == null || outboxPoller == null) {
                    throw new IllegalStateException("FCM worker 구성이 불완전합니다");
                  }
                  streamConsumer.start();
                  outboxPoller.start();
                  ready.set(true);
                  log.info("알림 워커가 소비자 그룹을 준비하고 처리를 시작했습니다.");
                })
            .subscribe();
  }

  @EventListener(ContextClosedEvent.class)
  public synchronized void stop() {
    started = false;
    ready.set(false);
    if (startupSubscription != null) {
      startupSubscription.dispose();
      startupSubscription = null;
    }
    NotificationWorkerMetrics queueMetrics = queueMetricsProvider.getIfAvailable();
    if (queueMetrics != null) {
      queueMetrics.stop();
    }
    FcmOutboxPoller outboxPoller = outboxPollerProvider.getIfAvailable();
    if (outboxPoller != null) {
      outboxPoller.stop();
    }
    FcmStreamConsumer streamConsumer = streamConsumerProvider.getIfAvailable();
    if (streamConsumer != null) {
      streamConsumer.stop();
    }
  }

  @Override
  public Health health() {
    return ready.get()
        ? Health.up().build()
        : Health.outOfService().withDetail("reason", "알림 소비자 그룹이 준비되지 않았습니다").build();
  }
}
