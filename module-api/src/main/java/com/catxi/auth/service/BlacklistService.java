package com.catxi.auth.service;

import com.catxi.auth.dto.BlacklistStatusRes;
import com.catxi.auth.infra.RefreshTokenRepository;
import com.catxi.auth.infra.TokenBlacklistRepository;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.domain.Member;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlacklistService {

  private final TokenBlacklistRepository tokenBlacklistRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final MemberRepository memberRepository;

  // 사용자를 블랙리스트에 추가 (영구)
  @Transactional
  public void addUserToBlacklistPermanent(Long userId) {
    Member member =
        memberRepository
            .findById(userId)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    // 사용자를 블랙리스트에 추가
    tokenBlacklistRepository.addUserToBlacklist(userId.toString());

    // 해당 사용자의 refreshToken을 Redis에서 삭제
    refreshTokenRepository.delete(member.getEmail());

    log.info("회원 블랙리스트 추가 및 RefreshToken 삭제: memberId={}", userId);
  }

  // 사용자를 블랙리스트에서 제거
  @Transactional
  public void removeUserFromBlacklist(Long userId) {
    Member member =
        memberRepository
            .findById(userId)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

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

  public BlacklistStatusRes getBlacklistStatus(Long userId) {
    boolean blacklisted = isUserBlacklisted(userId);
    String email = memberRepository.findById(userId).map(Member::getEmail).orElse("Unknown");
    return new BlacklistStatusRes(blacklisted, userId, email);
  }
}
