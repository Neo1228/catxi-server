package com.catxi.worker.deadletter;

import com.catxi.worker.deadletter.FcmDeadLetterReplayService.ReplayResult;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fcm.worker.replay-only", havingValue = "true")
public class FcmDeadLetterReplayRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(FcmDeadLetterReplayRunner.class);
  private static final Duration REPLAY_TIMEOUT = Duration.ofSeconds(30);

  private final FcmDeadLetterReplayService replayService;
  private final String deadLetterId;

  public FcmDeadLetterReplayRunner(
      FcmDeadLetterReplayService replayService,
      @Value("${fcm.dead-letter.replay-id:}") String deadLetterId) {
    this.replayService = replayService;
    this.deadLetterId = deadLetterId;
  }

  @Override
  public void run(ApplicationArguments args) {
    long id;
    try {
      id = Long.parseLong(deadLetterId);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "일회성 재처리에는 --fcm.dead-letter.replay-id=<DLQ ID>가 필요합니다", exception);
    }

    ReplayResult result = replayService.replay(id).block(REPLAY_TIMEOUT);
    if (result == null) {
      throw new IllegalStateException("FCM DLQ 재처리가 제한 시간 안에 완료되지 않았습니다");
    }
    log.info(
        "FCM DLQ 재처리 요청이 {} 처리되었습니다: dlqId={}, replayEventId={}",
        result.alreadyRequested() ? "이미" : "새로",
        result.deadLetterId(),
        result.replayEventId());
  }
}
