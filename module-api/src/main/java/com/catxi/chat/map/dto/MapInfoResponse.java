package com.catxi.chat.map.dto;

import com.catxi.common.domain.Location;
import java.util.List;

public record MapInfoResponse(Location departure, List<CoordinateResponse> coordinates) {}
