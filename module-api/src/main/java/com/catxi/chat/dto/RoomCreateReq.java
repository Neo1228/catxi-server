package com.catxi.chat.dto;

import com.catxi.common.domain.Location;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record RoomCreateReq(
    @NotNull Location startPoint,
    @NotNull Location endPoint,
    @NotNull @Min(1) @Max(4) Long recruitSize,
    @NotNull LocalDateTime departAt) {}
