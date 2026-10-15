package com.catxi.worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@SpringBootApplication(scanBasePackages = "com.catxi.worker")
public class CatxiWorkerApplication {

  public static void main(String[] args) {
    ConfigurableApplicationContext context =
        SpringApplication.run(CatxiWorkerApplication.class, args);
    if (context.getEnvironment().getProperty("fcm.worker.replay-only", Boolean.class, false)) {
      System.exit(SpringApplication.exit(context));
    }
  }

  @Bean
  ApplicationRunner validateWorkerConfiguration(
      Environment environment,
      @Value("${WORKER_DB_USER:}") String username,
      @Value("${WORKER_DB_PW:}") String password,
      @Value("${fcm.worker.enabled}") boolean notificationsEnabled,
      @Value("${fcm.worker.replay-only:false}") boolean replayOnly,
      @Value("${fcm.dead-letter.replay-id:}") String replayId) {
    return (ApplicationArguments args) -> {
      if (username.isBlank() || password.isBlank()) {
        throw new IllegalStateException("알림 워커의 WORKER_DB_USER와 WORKER_DB_PW를 설정해야 합니다");
      }
      if (replayOnly && notificationsEnabled) {
        throw new IllegalStateException("일회성 DLQ 재처리는 --fcm.worker.enabled=false로 실행해야 합니다");
      }
      if (!replayOnly && !replayId.isBlank()) {
        throw new IllegalStateException("DLQ 재처리 ID를 지정하려면 --fcm.worker.replay-only=true가 필요합니다");
      }
      if (environment.acceptsProfiles(Profiles.of("prod"))
          && !notificationsEnabled
          && !replayOnly) {
        throw new IllegalStateException("운영 알림 워커에서는 매칭 FCM 알림을 비활성화할 수 없습니다");
      }
    };
  }
}
