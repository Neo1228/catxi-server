package com.catxi.chat.dto;

import com.catxi.chat.domain.RoomStatus;
import com.catxi.common.domain.Location;
import java.time.LocalDateTime;

public record RoomCreateResponse(
    Long roomId,
    Location startPoint,
    Location endPoint,
    Long recruitSize,
    LocalDateTime departAt,
    RoomStatus status) {}
