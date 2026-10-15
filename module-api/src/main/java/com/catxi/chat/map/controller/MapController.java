package com.catxi.chat.map.controller;

import com.catxi.auth.security.CustomUserDetails;
import com.catxi.chat.map.dto.DepartureReq;
import com.catxi.chat.map.dto.MapInfoRes;
import com.catxi.chat.map.service.MapService;
import com.catxi.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/map")
public class MapController {

  private final MapService mapService;

  @GetMapping("/{roomId}/coordinates")
  @Operation(
      summary = "채팅방 참가자들의 좌표를 조회합니다",
      description =
          "해당 채팅방에 참여중인 사용자의 좌표를 조회합니다. "
              + "좌표는 레디스에 저장된 정보를 기반으로 하며, 5분 동안 갱신하지 않은 좌표는 반환되지 않습니다.")
  public ResponseEntity<ApiResponse<MapInfoRes>> getCoordinates(
      @PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        ApiResponse.success(mapService.getCoordinates(roomId, userDetails.getUsername())));
  }

  @PostMapping("/{roomId}/save-depart")
  @Operation(summary = "출발지 좌표 저장 API", description = "출발지 좌표를 레디스에 저장합니다.")
  public ResponseEntity<ApiResponse<Void>> saveDepart(
      @PathVariable Long roomId,
      @Valid @RequestBody DepartureReq request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    mapService.saveDepartureCoordinate(
        request.latitude(), request.longitude(), roomId, userDetails.getUsername());
    return ResponseEntity.ok(ApiResponse.successWithNoData());
  }
}
