package com.catxi.contract.chat;

import java.time.Duration;

public final class ChatPresenceKeys {
  public static final Duration LEASE = Duration.ofMinutes(2);

  private static final String ACTIVE_MARKER_FORMAT = "chat:active:room:%s:user:%s";
  private static final String SESSION_REGISTRY_FORMAT = "chat:active:sessions:room:%s:user:%s:v1";

  private ChatPresenceKeys() {}

  public static String activeMarker(String roomId, String memberId) {
    return ACTIVE_MARKER_FORMAT.formatted(roomId, memberId);
  }

  public static String sessionRegistry(String roomId, String memberId) {
    return SESSION_REGISTRY_FORMAT.formatted(roomId, memberId);
  }
}
