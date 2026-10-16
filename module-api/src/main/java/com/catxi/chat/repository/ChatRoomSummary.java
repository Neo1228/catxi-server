package com.catxi.chat.repository;

import com.catxi.chat.domain.RoomStatus;
import com.catxi.common.domain.Location;
import java.time.LocalDateTime;

public record ChatRoomSummary(
    Long roomId,
    Long hostId,
    Location startPoint,
    Location endPoint,
    Long recruitSize,
    Long currentSize,
    RoomStatus status,
    LocalDateTime departAt,
    LocalDateTime createdTime) {}
