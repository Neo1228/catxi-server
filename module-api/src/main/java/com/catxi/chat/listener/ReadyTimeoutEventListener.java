package com.catxi.chat.listener;

import com.catxi.chat.dto.ReadyTimeoutRequestedEvent;
import com.catxi.chat.service.TimerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReadyTimeoutEventListener {
  private final TimerService timerService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ReadyTimeoutRequestedEvent event) {
    try {
      timerService.scheduleReadyTimeout(event.roomId().toString());
    } catch (Exception e) {
      log.error("준비 타임아웃 예약 실패: roomId={}", event.roomId(), e);
    }
  }
}
