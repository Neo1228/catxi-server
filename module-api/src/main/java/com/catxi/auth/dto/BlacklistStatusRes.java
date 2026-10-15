package com.catxi.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "checkBlacklist", description = "블랙리스트 조회 응답")
public record BlacklistStatusRes(
    @Schema(description = "블랙리스트 등록 여부", example = "true") boolean isBlacklisted,
    @Schema(description = "사용자 ID", example = "1") Long userId,
    @Schema(description = "사용자 이메일", example = "user@example.com") String email) {}
