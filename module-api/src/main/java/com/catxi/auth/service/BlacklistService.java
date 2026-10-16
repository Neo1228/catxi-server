package com.catxi.auth.service;

import com.catxi.auth.infrastructure.RefreshTokenRepository;
import com.catxi.auth.infrastructure.TokenBlacklistRepository;
import com.catxi.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlacklistService {

  private final TokenBlacklistRepository tokenBlacklistRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final MemberService memberService;

  // 사용자를 블랙리스트에 추가 (영구)
  public void addUserToBlacklistPermanent(Long userId) {
    MemberService.MemberAccount member = memberService.findAccountById(userId);

    // 사용자를 블랙리스트에 추가
    tokenBlacklistRepository.addUserToBlacklist(userId.toString());

    // 해당 사용자의 refreshToken을 Redis에서 삭제
    refreshTokenRepository.delete(member.email());

    log.info("회원 블랙리스트 추가 및 RefreshToken 삭제: memberId={}", userId);
  }

  // 사용자를 블랙리스트에서 제거
  public void removeUserFromBlacklist(Long userId) {
    tokenBlacklistRepository.removeUserFromBlacklist(userId.toString());
    log.info("회원 블랙리스트 해제: memberId={}", userId);
  }

  // 사용자가 블랙리스트에 있는지 확인
  public boolean isUserBlacklisted(Long userId) {
    return tokenBlacklistRepository.isUserBlacklisted(userId.toString());
  }

  public boolean isTokenBlacklisted(String token) {
    return tokenBlacklistRepository.isTokenBlacklisted(token);
  }

  public BlacklistStatus getBlacklistStatus(Long userId) {
    boolean blacklisted = isUserBlacklisted(userId);
    String email =
        memberService
            .findAccountByIdOptional(userId)
            .map(MemberService.MemberAccount::email)
            .orElse("Unknown");
    return new BlacklistStatus(blacklisted, userId, email);
  }

  public record BlacklistStatus(boolean blacklisted, Long userId, String email) {}
}
