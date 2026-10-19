package com.catxi.chat.scheduler;

import com.catxi.chat.broadcast.ReadyResultPublisher;
import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.domain.RoomStatus;
import com.catxi.chat.error.ChatRoomErrorCode;
import com.catxi.chat.repository.ChatRoomRepository;
import com.catxi.chat.service.ReadyService;
import com.catxi.common.api.exception.CatxiException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReadyTimeoutScheduler {
  private static final long READY_TIMEOUT_RETRY_SECONDS = 30;
  private static final int RECOVERY_BATCH_SIZE = 100;

  private final ReadyResultPublisher readyResultPublisher;
  private final TaskScheduler taskScheduler;
  private final ChatRoomRepository chatRoomRepository;
  private final ReadyService readyService;

  public ReadyTimeoutScheduler(
      @Qualifier("readyTimeoutTaskScheduler") TaskScheduler taskScheduler,
      ChatRoomRepository chatRoomRepository,
      ReadyService readyService,
      ReadyResultPublisher readyResultPublisher) {
    this.taskScheduler = taskScheduler;
    this.chatRoomRepository = chatRoomRepository;
    this.readyService = readyService;
    this.readyResultPublisher = readyResultPublisher;
  }

  public void scheduleReadyTimeout(Long roomId) {
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    if (room.getStatus() != RoomStatus.READY_LOCKED || room.getReadyTimeoutAt() == null) {
      return;
    }

    taskScheduler.schedule(
        () -> {
          processReadyTimeoutSafely(room.getRoomId());
        },
        room.getReadyTimeoutAt().atZone(ZoneId.systemDefault()).toInstant());
  }

  @Scheduled(fixedDelayString = "${chat.ready-timeout.recovery-interval-ms:5000}")
  public void recoverDueReadyTimeouts() {
    try {
      List<Long> dueRoomIds =
          chatRoomRepository.findDueReadyTimeoutIds(
              RoomStatus.READY_LOCKED, LocalDateTime.now(), PageRequest.of(0, RECOVERY_BATCH_SIZE));
      for (Long roomId : dueRoomIds) {
        processReadyTimeoutSafely(roomId);
      }
    } catch (Exception e) {
      log.error("만료된 준비 타임아웃 조회 실패", e);
    }
  }

  private void processReadyTimeoutSafely(Long roomId) {
    try {
      readyService
          .processReadyTimeout(roomId)
          .ifPresent(
              result -> {
                readyResultPublisher.publish(roomId, result.eventType(), result.eventContent());
                log.info(
                    "[ReadyTimeoutScheduler] 방 상태 업데이트 완료: {} at {}", roomId, LocalDateTime.now());
              });
    } catch (Exception e) {
      try {
        LocalDateTime now = LocalDateTime.now();
        int updated =
            chatRoomRepository.postponeDueReadyTimeout(
                roomId, RoomStatus.READY_LOCKED, now, now.plusSeconds(READY_TIMEOUT_RETRY_SECONDS));
        if (updated > 0) {
          log.warn("준비 타임아웃 처리 실패로 재시도 시각을 늦췄습니다: roomId={}", roomId, e);
        }
      } catch (Exception retryError) {
        e.addSuppressed(retryError);
        log.error("준비 타임아웃 처리와 재시도 예약이 모두 실패했습니다: roomId={}", roomId, e);
      }
    }
  }
}
