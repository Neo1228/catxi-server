package com.catxi.chat.dto;

import com.catxi.chat.domain.RoomStatus;
import com.catxi.common.domain.Location;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

public record ChatRoomInfoRes(
    Long currentSize,
    Long recruitSize,
    RoomStatus roomStatus,
    String hostEmail,
    String hostNickname,
    List<String> participantEmails,
    List<String> participantNicknames,
    Location startPoint,
    Location endPoint,
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss",
            timezone = "Asia/Seoul")
        LocalDateTime departAt) {}
