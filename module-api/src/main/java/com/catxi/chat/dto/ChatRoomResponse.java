package com.catxi.chat.dto;

import com.catxi.chat.domain.RoomStatus;
import com.catxi.common.domain.MeetingPoint;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record ChatRoomResponse(
    Long roomId,
    Long hostId,
    String hostName,
    String hostNickname,
    Integer matchCount,
    MeetingPoint startPoint,
    MeetingPoint endPoint,
    Long recruitSize,
    Long currentSize,
    RoomStatus status,
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss",
            timezone = "Asia/Seoul")
        LocalDateTime departAt,
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss",
            timezone = "Asia/Seoul")
        LocalDateTime createdTime) {}
