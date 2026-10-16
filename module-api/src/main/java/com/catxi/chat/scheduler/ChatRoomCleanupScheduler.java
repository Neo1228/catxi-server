package com.catxi.chat.scheduler;

import com.catxi.chat.service.ChatRoomCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomCleanupScheduler {

  private static final int MAX_BATCHES_PER_RUN = 5;

  private final ChatRoomCleanupService cleanupService;

  @Scheduled(fixedDelay = 60_000L)
  public void runCleanup() {
    int deletedRooms = 0;
    for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
      int deletedInBatch = cleanupService.deleteExpiredChatRoomsBatch();
      if (deletedInBatch == 0) {
        break;
      }
      deletedRooms += deletedInBatch;
    }

    if (deletedRooms > 0) {
      log.info("만료된 채팅방 정리 완료: {}개", deletedRooms);
    }
  }
}
