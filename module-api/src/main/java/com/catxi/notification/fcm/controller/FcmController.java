package com.catxi.notification.fcm.controller;

import com.catxi.common.api.ApiResponse;
import com.catxi.notification.fcm.dto.FcmFidUpdateRequest;
import com.catxi.notification.fcm.dto.FcmTokenUpdateRequest;
import com.catxi.notification.fcm.dto.FcmTokenUpdateResponse;
import com.catxi.notification.fcm.service.FcmRegistrationService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class FcmController {

  private final FcmRegistrationService fcmRegistrationService;

  @PutMapping("/token")
  public ResponseEntity<ApiResponse<FcmTokenUpdateResponse>> updateFcmToken(
      Principal principal, @Valid @RequestBody FcmTokenUpdateRequest request) {

    String email = principal.getName();
    var updated = fcmRegistrationService.updateFcmToken(email, request.token());
    FcmTokenUpdateResponse response =
        new FcmTokenUpdateResponse(
            updated.memberId(),
            updated.fid() == null ? updated.tokenUpdatedAt() : updated.fidUpdatedAt(),
            updated.token() != null || updated.fid() != null);

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PutMapping("/fid")
  public ResponseEntity<ApiResponse<Void>> updateFcmFid(
      @Valid @RequestBody FcmFidUpdateRequest request, Principal principal) {
    fcmRegistrationService.updateFcmFid(principal.getName(), request.fid());
    return ResponseEntity.ok(ApiResponse.successWithNoData());
  }

  @DeleteMapping({"/token", "/fid"})
  public ResponseEntity<ApiResponse<Void>> deleteFcmRegistration(Principal principal) {

    String email = principal.getName();
    fcmRegistrationService.deleteFcmRegistration(email);

    return ResponseEntity.ok(ApiResponse.successWithNoData());
  }
}
