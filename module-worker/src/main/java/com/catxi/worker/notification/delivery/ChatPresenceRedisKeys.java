package com.catxi.worker.notification.delivery;

public final class ChatPresenceRedisKeys {
  private static final String ACTIVE_MARKER_FORMAT = "chat:active:room:%s:user:%s";
  private static final String SESSION_REGISTRY_FORMAT = "chat:active:sessions:room:%s:user:%s:v1";

  private ChatPresenceRedisKeys() {}

  public static String activeMarkerKey(String roomId, String memberId) {
    return ACTIVE_MARKER_FORMAT.formatted(roomId, memberId);
  }

  public static String sessionRegistryKey(String roomId, String memberId) {
    return SESSION_REGISTRY_FORMAT.formatted(roomId, memberId);
  }
}
