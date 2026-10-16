package com.catxi.chat.event;

public record RoomDeletedEvent(Long roomId, String hostNickname) {}
