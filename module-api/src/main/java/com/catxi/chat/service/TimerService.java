package com.catxi.chat.service;

import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.domain.RoomStatus;
import com.catxi.chat.dto.ChatRoomAccessRevoked;
import com.catxi.chat.dto.RoomEventMessage;
import com.catxi.chat.error.ChatRoomErrorCode;
import com.catxi.chat.repository.ChatParticipantRepository;
import com.catxi.chat.repository.ChatRoomRepository;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.notification.contract.FcmNotificationEvent;
import com.catxi.notification.fcm.service.FcmOutboxService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class TimerService {
  private static final long READY_TIMEOUT_RETRY_SECONDS = 30;
  private static final int RECOVERY_BATCH_SIZE = 100;

  private final ObjectMapper objectMapper;
  private final RedisTemplate<String, String> redisTemplate;
  private final TaskScheduler taskScheduler;
  private final ChatRoomRepository chatRoomRepository;
  private final ChatParticipantRepository chatParticipantRepository;
  private final ChatMessageService chatMessageService;
  private final MatchHistoryRecorder matchHistoryRecorder;
  private final FcmOutboxService fcmOutboxService;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final TransactionTemplate transactionTemplate;

  public TimerService(
      RedisTemplate<String, String> redisTemplate,
      @Qualifier("readyTimeoutScheduler") TaskScheduler taskScheduler,
      ChatRoomRepository chatRoomRepository,
      ChatParticipantRepository chatParticipantRepository,
      ChatMessageService chatMessageService,
      MatchHistoryRecorder matchHistoryRecorder,
      FcmOutboxService fcmOutboxService,
      ApplicationEventPublisher applicationEventPublisher,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    this.redisTemplate = redisTemplate;
    this.taskScheduler = taskScheduler;
    this.chatRoomRepository = chatRoomRepository;
    this.chatParticipantRepository = chatParticipantRepository;
    this.chatMessageService = chatMessageService;
    this.matchHistoryRecorder = matchHistoryRecorder;
    this.fcmOutboxService = fcmOutboxService;
    this.applicationEventPublisher = applicationEventPublisher;
    this.objectMapper = objectMapper;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  public void scheduleReadyTimeout(String roomId) {
    ChatRoom room =
        chatRoomRepository
            .findById(Long.valueOf(roomId))
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
      checkAndUpdateRoomStatus(roomId.toString());
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

  public void checkAndUpdateRoomStatus(String roomId) {
    TimeoutResult result =
        transactionTemplate.execute(status -> updateRoomStatus(Long.valueOf(roomId)));

    if (result == null) {
      return;
    }

    if (result.eventType() != null) {
      publishRoomResult(Long.valueOf(roomId), result.eventType(), result.eventContent());
    }
    log.info("[TimerService] 방 상태 업데이트 완료: {} at {}", roomId, LocalDateTime.now());
  }

  private TimeoutResult updateRoomStatus(Long roomId) {
    ChatRoom room =
        chatRoomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    if (room.getStatus() != RoomStatus.READY_LOCKED
        || room.getReadyTimeoutAt() == null
        || room.getReadyTimeoutAt().isAfter(LocalDateTime.now())) {
      return null;
    }

    Long savedCount = room.getReadyParticipantCount();
    long currentCount = chatParticipantRepository.countByChatRoomAndIsReady(room, true);
    if (savedCount == null) {
      log.warn("준비 요청 당시 인원 스냅샷이 없어 매칭을 보류합니다: roomId={}", roomId);
    }
    if (savedCount != null && savedCount > 1 && savedCount == currentCount) {
      room.matchedStatus(RoomStatus.MATCHED);
      chatRoomRepository.save(room);
      matchHistoryRecorder.recordForMatchedRoom(room);
      // 실시간 전송을 놓쳐도 채팅 이력과 푸시 작업이 남도록 상태 변경과 함께 저장합니다.
      List<Long> participantIds = chatParticipantRepository.findMemberIdsByChatRoom(room);
      fcmOutboxService.enqueue(FcmNotificationEvent.createMatchCompleted(participantIds, roomId));
      chatMessageService.sendSystemMessage(roomId, "모든 참가자가 준비되어 매칭이 완료되었습니다.");
      return new TimeoutResult("MATCHED", "모든 참가자가 준비되었습니다. 매칭이 완료되었습니다");
    }

    List<String> removeNickNames =
        chatParticipantRepository.findNicknamesByChatRoomAndIsReadyFalse(room);
    chatParticipantRepository.findByChatRoom(room).stream()
        .filter(participant -> !participant.isReady())
        .forEach(
            participant ->
                applicationEventPublisher.publishEvent(
                    new ChatRoomAccessRevoked(roomId, participant.getMember().getId())));
    chatParticipantRepository.deleteAllByChatRoomAndIsReadyFalse(room);
    chatParticipantRepository.clearReadyStatusForNonHosts(room.getRoomId());
    for (String nickName : removeNickNames) {
      chatMessageService.sendSystemMessage(roomId, nickName + " 님이 퇴장하셨습니다.");
    }

    room.setStatus(RoomStatus.WAITING);
    chatRoomRepository.save(room);
    String message =
        savedCount == null
            ? "준비 요청 당시 인원을 확인할 수 없어 대기 상태로 돌아갑니다. 다시 요청해 주세요."
            : "일부 참가자가 준비하지 않아 대기 상태로 돌아갑니다.";
    return new TimeoutResult("RETURN_WAITING", message);
  }

  private void publishRoomResult(Long roomId, String type, String content) {
    try {
      RoomEventMessage event = new RoomEventMessage(roomId, type, content);
      String json = objectMapper.writeValueAsString(event);
      redisTemplate.convertAndSend("readyresult:" + roomId, json);
    } catch (Exception e) {
      log.error("준비 결과 발행 실패: roomId={}", roomId, e);
    }
  }

  private record TimeoutResult(String eventType, String eventContent) {}
}
