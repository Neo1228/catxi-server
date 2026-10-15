package com.catxi.chat.map.dto;

import com.catxi.common.domain.Location;
import java.util.List;

public record MapInfoRes(Location departure, List<CoordinateRes> coordinates) {}
