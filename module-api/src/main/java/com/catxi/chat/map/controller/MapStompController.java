package com.catxi.chat.map.controller;

import com.catxi.chat.map.dto.CoordinateUpdateRequest;
import com.catxi.chat.map.service.MapService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@RequiredArgsConstructor
@Controller
public class MapStompController {

  private final MapService mapService;

  @MessageMapping("/map/{roomId}")
  public void sendCoordinate(
      @DestinationVariable Long roomId,
      @Valid CoordinateUpdateRequest request,
      Principal principal) {
    mapService.shareCoordinate(
        roomId, principal.getName(), request.latitude(), request.longitude());
  }
}
