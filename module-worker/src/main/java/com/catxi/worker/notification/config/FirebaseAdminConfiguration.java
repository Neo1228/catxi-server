package com.catxi.worker.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "fcm.worker.enabled", havingValue = "true")
public class FirebaseAdminConfiguration {

  private static final String APP_NAME = "catxi-worker";

  @Bean(destroyMethod = "delete")
  FirebaseApp firebaseApp(@Value("${fcm.service-account-file}") String serviceAccountPath)
      throws Exception {
    FirebaseApp existing =
        FirebaseApp.getApps().stream()
            .filter(app -> APP_NAME.equals(app.getName()))
            .findFirst()
            .orElse(null);
    if (existing != null) {
      return existing;
    }

    GoogleCredentials credentials;
    if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
      credentials = GoogleCredentials.getApplicationDefault();
    } else if (serviceAccountPath.startsWith("classpath:")) {
      String resourceName = serviceAccountPath.substring("classpath:".length());
      InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
      if (stream == null) {
        throw new IllegalStateException("Firebase 서비스 계정 파일을 찾을 수 없습니다: " + resourceName);
      }
      try (stream) {
        credentials = GoogleCredentials.fromStream(stream);
      }
    } else {
      try (InputStream stream = Files.newInputStream(Path.of(serviceAccountPath))) {
        credentials = GoogleCredentials.fromStream(stream);
      }
    }

    FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
    return FirebaseApp.initializeApp(options, APP_NAME);
  }

  @Bean
  FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
    return FirebaseMessaging.getInstance(firebaseApp);
  }
}
