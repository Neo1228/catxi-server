package com.catxi.chat.broadcast.message;

public record RoomEventMessage(Long roomId, String type, String message) {}
