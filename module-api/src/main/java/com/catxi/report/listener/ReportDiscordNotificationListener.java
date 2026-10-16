package com.catxi.report.listener;

import com.catxi.report.event.ReportCreatedEvent;
import java.net.URI;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@ConditionalOnProperty(name = "discord.reports.enabled", havingValue = "true")
public class ReportDiscordNotificationListener {

  private final TaskExecutor taskExecutor;
  private final RestClient restClient;
  private final String webhookUrl;

  public ReportDiscordNotificationListener(
      @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
      RestClient.Builder restClientBuilder,
      @Value("${discord.webhook.url:}") String webhookUrl) {
    if (!StringUtils.hasText(webhookUrl)) {
      throw new IllegalStateException(
          "DISCORD_REPORTS_ENABLED=true이면 DISCORD_WEBHOOK_URL을 설정해야 합니다");
    }
    URI uri = URI.create(webhookUrl);
    if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
      throw new IllegalStateException("Discord 웹훅 주소는 HTTPS URL이어야 합니다");
    }

    this.taskExecutor = taskExecutor;
    this.restClient = restClientBuilder.build();
    this.webhookUrl = webhookUrl;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReportCreated(ReportCreatedEvent event) {
    try {
      // 커밋 후 비동기 전송이므로 프로세스가 종료되면 Discord 알림은 유실될 수 있습니다.
      taskExecutor.execute(() -> send(event));
    } catch (TaskRejectedException exception) {
      log.error("Discord 신고 알림 작업을 예약하지 못했습니다: reportId={}", event.reportId());
    }
  }

  private void send(ReportCreatedEvent event) {
    String content =
        """
        새 신고가 접수되었습니다.
        신고 ID: %d
        채팅방 ID: %d
        접수 시각: %s
        신고 기록에서 내용을 확인하세요.
        """
            .formatted(event.reportId(), event.roomId(), event.createdAt());

    try {
      restClient
          .post()
          .uri(webhookUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("content", content))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException exception) {
      log.warn(
          "Discord 신고 알림 전송에 실패했습니다: reportId={}, errorType={}",
          event.reportId(),
          exception.getClass().getSimpleName());
    }
  }
}
