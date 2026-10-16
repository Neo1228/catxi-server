package com.catxi.chat.controller;

import com.catxi.chat.service.ReadyService;
import com.catxi.common.api.ApiResponse;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ready")
public class ReadyController {
  private final ReadyService readyService;

  @PostMapping("/request/{roomId}")
  public ResponseEntity<ApiResponse<Void>> requestReady(
      @PathVariable Long roomId, Principal principal) {
    readyService.requestReady(roomId, principal.getName());

    return ResponseEntity.ok(ApiResponse.successWithNoData());
  }

  @PostMapping("/accept/{roomId}")
  public ResponseEntity<ApiResponse<Void>> acceptReady(
      @PathVariable Long roomId, Principal principal) {
    readyService.acceptReady(roomId, principal.getName());

    return ResponseEntity.ok(ApiResponse.successWithNoData());
  }

  @PostMapping("/reject/{roomId}")
  public ResponseEntity<ApiResponse<Void>> rejectReady(
      @PathVariable Long roomId, Principal principal) {
    readyService.rejectReady(roomId, principal.getName());

    return ResponseEntity.ok(ApiResponse.successWithNoData());
  }
}
