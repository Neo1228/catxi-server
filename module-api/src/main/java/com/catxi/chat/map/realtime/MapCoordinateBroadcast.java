package com.catxi.chat.map.realtime;

public record MapCoordinateBroadcast(
    Long roomId,
    String email,
    String name,
    String nickname,
    Double latitude,
    Double longitude,
    Double distance) {}
