package com.catxi.chat.map.dto;

public record CoordinateRes(
    Long roomId,
    String email,
    String name,
    String nickname,
    Double latitude,
    Double longitude,
    Double distance) {}
