package com.catxi.notification.fcm.controller;

import com.catxi.auth.security.CustomUserDetails;
import com.catxi.common.api.ApiResponse;
import com.catxi.notification.fcm.dto.FcmActiveStatusReq;
import com.catxi.notification.fcm.dto.FcmTokenUpdateReq;
import com.catxi.notification.fcm.dto.FcmTokenUpdateRes;
import com.catxi.notification.fcm.service.FcmActiveStatusService;
import com.catxi.notification.fcm.service.FcmTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class FcmController {

  private final FcmTokenService fcmTokenService;
  private final FcmActiveStatusService fcmActiveStatusService;

  @PutMapping("/token")
  public ResponseEntity<ApiResponse<FcmTokenUpdateRes>> updateFcmToken(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody FcmTokenUpdateReq request) {

    String email = userDetails.getUsername();
    FcmTokenUpdateRes response = fcmTokenService.updateFcmToken(email, request);

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PutMapping("/active-status")
  public ResponseEntity<ApiResponse<Void>> updateActiveStatus(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody FcmActiveStatusReq request) {

    String email = userDetails.getUsername();
    fcmActiveStatusService.updateUserActiveStatus(email, request.roomId(), request.isActive());

    return ResponseEntity.ok(ApiResponse.successWithNoData());
  }

  @DeleteMapping("/token")
  public ResponseEntity<ApiResponse<Void>> deleteFcmToken(
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    String email = userDetails.getUsername();
    fcmTokenService.deleteFcmToken(email);

    return ResponseEntity.ok(ApiResponse.successWithNoData());
  }
}
