package com.catxi.chat.scheduler;

import com.catxi.chat.event.ReadyTimeoutRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReadyTimeoutEventListener {
  private final ReadyTimeoutScheduler readyTimeoutScheduler;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ReadyTimeoutRequestedEvent event) {
    try {
      readyTimeoutScheduler.scheduleReadyTimeout(event.roomId());
    } catch (Exception e) {
      log.error("준비 타임아웃 예약 실패: roomId={}", event.roomId(), e);
    }
  }
}
