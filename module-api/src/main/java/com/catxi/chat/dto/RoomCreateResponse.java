package com.catxi.chat.dto;

import com.catxi.chat.domain.RoomStatus;
import com.catxi.common.domain.MeetingPoint;
import java.time.LocalDateTime;

public record RoomCreateResponse(
    Long roomId,
    MeetingPoint startPoint,
    MeetingPoint endPoint,
    Long recruitSize,
    LocalDateTime departAt,
    RoomStatus status) {}
