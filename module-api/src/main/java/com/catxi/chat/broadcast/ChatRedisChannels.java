package com.catxi.chat.broadcast;

public final class ChatRedisChannels {

  public static final String CHAT = "chat";
  public static final String MAP = "map";
  public static final String READY_PREFIX = "ready:";
  public static final String PARTICIPANTS_PREFIX = "participants:";
  public static final String KICK_PREFIX = "kick:";
  public static final String ROOM_ACCESS_REVOKED_PREFIX = "roomaccessrevoked:";
  public static final String ROOM_DELETED_PREFIX = "roomdeleted:";
  public static final String READY_RESULT_PREFIX = "readyresult:";

  private ChatRedisChannels() {}
}
