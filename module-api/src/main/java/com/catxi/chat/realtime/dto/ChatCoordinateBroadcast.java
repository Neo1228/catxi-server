package com.catxi.chat.realtime.dto;

public record ChatCoordinateBroadcast(
    Long roomId,
    String email,
    String name,
    String nickname,
    Double latitude,
    Double longitude,
    Double distance) {}
