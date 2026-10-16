package com.catxi.notification.fcm.service;

import com.catxi.notification.fcm.domain.FcmOutbox;
import com.catxi.notification.fcm.event.FcmNotificationEvent;
import com.catxi.notification.fcm.repository.FcmOutboxRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class FcmOutboxService {

  private final FcmOutboxRepository fcmOutboxRepository;
  private final Environment environment;
  private final boolean notificationsEnabled;

  public FcmOutboxService(
      FcmOutboxRepository fcmOutboxRepository,
      Environment environment,
      @Value("${fcm.notifications.enabled}") boolean notificationsEnabled) {
    this.fcmOutboxRepository = fcmOutboxRepository;
    this.environment = environment;
    this.notificationsEnabled = notificationsEnabled;
  }

  @PostConstruct
  void logNotificationConfiguration() {
    if (!notificationsEnabled) {
      for (String profile : environment.getActiveProfiles()) {
        if ("prod".equals(profile)) {
          throw new IllegalStateException("운영 환경에서는 매칭 완료 FCM 알림을 비활성화할 수 없습니다");
        }
      }
      log.warn("FCM 알림 생성을 비활성화했습니다. 새 알림은 아웃박스에 저장하지 않습니다");
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(FcmNotificationEvent event) {
    if (!notificationsEnabled) {
      return;
    }

    fcmOutboxRepository.save(
        FcmOutbox.builder()
            .eventId(event.eventId())
            .eventType(event.type())
            .payload(event)
            .build());
  }
}
