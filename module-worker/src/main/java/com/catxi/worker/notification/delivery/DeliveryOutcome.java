package com.catxi.worker.notification.delivery;

import java.util.List;
import java.util.Map;

public record DeliveryOutcome(List<Long> retryMemberIds, Map<Long, String> terminalFailures) {

  public DeliveryOutcome {
    retryMemberIds = List.copyOf(retryMemberIds);
    terminalFailures = Map.copyOf(terminalFailures);
  }

  public static DeliveryOutcome completed() {
    return new DeliveryOutcome(List.of(), Map.of());
  }
}
