package com.catxi.chat.dto;

public record RoomEventMessage(Long roomId, String type, String message) {}
