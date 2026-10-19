package com.catxi.chat.realtime.message;

public record RoomEventMessage(Long roomId, String type, String message) {}
