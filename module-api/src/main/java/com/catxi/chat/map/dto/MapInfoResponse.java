package com.catxi.chat.map.dto;

import com.catxi.common.domain.MeetingPoint;
import java.util.List;

public record MapInfoResponse(MeetingPoint departure, List<CoordinateResponse> coordinates) {}
