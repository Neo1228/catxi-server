package com.catxi.worker.notification;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("notificationWorker")
public class NotificationWorkerReadiness implements HealthIndicator {

  private final AtomicBoolean ready;

  public NotificationWorkerReadiness(
      @Value("${fcm.notifications.enabled:false}") boolean notificationsEnabled) {
    this.ready = new AtomicBoolean(!notificationsEnabled);
  }

  public void markReady() {
    ready.set(true);
  }

  public void markNotReady() {
    ready.set(false);
  }

  @Override
  public Health health() {
    return ready.get()
        ? Health.up().build()
        : Health.outOfService().withDetail("reason", "알림 소비자 그룹이 준비되지 않았습니다").build();
  }
}
