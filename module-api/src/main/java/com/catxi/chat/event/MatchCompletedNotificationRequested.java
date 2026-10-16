package com.catxi.chat.event;

import java.util.List;

public record MatchCompletedNotificationRequested(List<Long> targetMemberIds, Long roomId) {
  public MatchCompletedNotificationRequested {
    targetMemberIds = List.copyOf(targetMemberIds);
  }
}
