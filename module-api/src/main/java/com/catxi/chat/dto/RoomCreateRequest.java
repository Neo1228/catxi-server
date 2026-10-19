package com.catxi.chat.dto;

import com.catxi.common.domain.MeetingPoint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record RoomCreateRequest(
    @NotNull MeetingPoint startPoint,
    @NotNull MeetingPoint endPoint,
    @NotNull @Min(1) @Max(4) Long recruitSize,
    @NotNull LocalDateTime departAt) {}
