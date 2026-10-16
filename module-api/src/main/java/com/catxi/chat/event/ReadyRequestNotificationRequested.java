package com.catxi.chat.event;

import java.util.List;

public record ReadyRequestNotificationRequested(List<Long> targetMemberIds, Long roomId) {
  public ReadyRequestNotificationRequested {
    targetMemberIds = List.copyOf(targetMemberIds);
  }
}
