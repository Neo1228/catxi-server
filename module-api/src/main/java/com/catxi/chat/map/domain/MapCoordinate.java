package com.catxi.chat.map.domain;

public record MapCoordinate(
    Long roomId,
    String email,
    String memberName,
    String nickname,
    Double latitude,
    Double longitude,
    Double distance) {}
