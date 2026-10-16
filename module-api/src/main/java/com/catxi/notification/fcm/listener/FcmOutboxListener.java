package com.catxi.notification.fcm.listener;

import com.catxi.chat.event.ChatMessageNotificationRequested;
import com.catxi.chat.event.MatchCompletedNotificationRequested;
import com.catxi.chat.event.ReadyRequestNotificationRequested;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.notification.fcm.error.FcmErrorCode;
import com.catxi.notification.fcm.event.FcmNotificationEvent;
import com.catxi.notification.fcm.service.FcmOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class FcmOutboxListener {
  private final FcmOutboxService fcmOutboxService;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onChatMessage(ChatMessageNotificationRequested request) {
    fcmOutboxService.enqueue(
        FcmNotificationEvent.createChatMessage(
            request.targetMemberId(), request.roomId(), request.messageId()));
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onReadyRequest(ReadyRequestNotificationRequested request) {
    try {
      fcmOutboxService.enqueue(
          FcmNotificationEvent.createReadyRequest(request.targetMemberIds(), request.roomId()));
    } catch (RuntimeException exception) {
      log.error("준비 요청 알림 저장 실패: roomId={}", request.roomId(), exception);
      throw new CatxiException(FcmErrorCode.FCM_OUTBOX_SAVE_FAILED);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onMatchCompleted(MatchCompletedNotificationRequested request) {
    fcmOutboxService.enqueue(
        FcmNotificationEvent.createMatchCompleted(request.targetMemberIds(), request.roomId()));
  }
}
