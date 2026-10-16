package com.catxi.auth.controller;

import com.catxi.auth.dto.BlacklistStatusResponse;
import com.catxi.auth.service.BlacklistService;
import com.catxi.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class BlacklistController {

  private final BlacklistService blacklistService;

  // admin 헬스 체크
  @GetMapping("/health")
  public String checkAdmin() {
    return "관리자 좋은데요~";
  }

  // 블랙리스트 등록
  @Operation(summary = "사용자 ID로 블랙리스트 등록", description = "사용자를 블랙리스트에 등록합니다.")
  @PostMapping("/uID/{userId}")
  public ApiResponse<?> addUserToBlacklistById(@PathVariable Long userId) {

    blacklistService.addUserToBlacklistPermanent(userId);
    return ApiResponse.success("사용자가 블랙리스트에 등록되었습니다.");
  }

  // 블랙리스트 해제
  @Operation(summary = "사용자 ID로 블랙리스트 해제", description = "사용자를 블랙리스트에서 해제합니다.")
  @DeleteMapping("/uID/{userId}")
  public ApiResponse<?> removeUserFromBlacklistById(@PathVariable Long userId) {

    blacklistService.removeUserFromBlacklist(userId);
    return ApiResponse.success("사용자가 블랙리스트에서 해제되었습니다.");
  }

  // 블랙리스트 여부 조회
  @Operation(summary = "사용자 ID로 블랙리스트 상태 확인", description = "사용자가 블랙리스트에 등록되어 있는지 확인합니다.")
  @GetMapping("/blacklist/uID/{userId}")
  public ApiResponse<BlacklistStatusResponse> checkBlacklistStatusById(@PathVariable Long userId) {
    BlacklistService.BlacklistStatus status = blacklistService.getBlacklistStatus(userId);
    return ApiResponse.success(
        new BlacklistStatusResponse(status.blacklisted(), status.userId(), status.email()));
  }
}
