package com.catxi.chat.domain;

import com.catxi.common.domain.Location;
import java.time.LocalDateTime;

public record ChatRoomSummary(
    Long roomId,
    Long hostId,
    String hostName,
    String hostNickname,
    Integer matchCount,
    Location startPoint,
    Location endPoint,
    Long recruitSize,
    Long currentSize,
    RoomStatus status,
    LocalDateTime departAt,
    LocalDateTime createdTime) {}
