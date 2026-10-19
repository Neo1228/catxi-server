package com.catxi.chat.controller;

import com.catxi.chat.dto.ChatActiveStatusRequest;
import com.catxi.chat.presence.ChatPresenceService;
import com.catxi.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class ChatPresenceController {

  private final ChatPresenceService chatPresenceService;

  @PutMapping("/active-status")
  public ResponseEntity<ApiResponse<Void>> updateActiveStatus(
      Principal principal, @Valid @RequestBody ChatActiveStatusRequest request) {
    chatPresenceService.updateUserActiveStatus(
        principal.getName(), request.roomId(), request.isActive());
    return ResponseEntity.ok(ApiResponse.successWithNoData());
  }
}
