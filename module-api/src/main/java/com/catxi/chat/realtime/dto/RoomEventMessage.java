package com.catxi.chat.realtime.dto;

public record RoomEventMessage(Long roomId, String type, String message) {}
