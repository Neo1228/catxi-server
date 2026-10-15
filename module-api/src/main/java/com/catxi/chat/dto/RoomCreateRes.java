package com.catxi.chat.dto;

import com.catxi.chat.domain.RoomStatus;
import com.catxi.common.domain.Location;
import java.time.LocalDateTime;

public record RoomCreateRes(
    Long roomId,
    Location startPoint,
    Location endPoint,
    Long recruitSize,
    LocalDateTime departAt,
    RoomStatus status) {}
