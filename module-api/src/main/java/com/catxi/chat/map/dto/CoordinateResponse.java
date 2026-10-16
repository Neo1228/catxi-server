package com.catxi.chat.map.dto;

public record CoordinateResponse(
    Long roomId,
    String email,
    String name,
    String nickname,
    Double latitude,
    Double longitude,
    Double distance) {}
