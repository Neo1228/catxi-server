package com.catxi.auth.service;

import com.catxi.auth.error.AuthErrorCode;
import com.catxi.auth.infrastructure.RefreshTokenRepository;
import com.catxi.auth.infrastructure.TokenBlacklistRepository;
import com.catxi.auth.jwt.JwtTokenParser;
import com.catxi.auth.jwt.JwtTokenProvider;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.domain.MemberStatus;
import com.catxi.member.service.MemberService;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

  private final AuthenticationManager authenticationManager;
  private final JwtTokenParser jwtTokenParser;
  private final JwtTokenProvider jwtTokenProvider;
  private final MemberService memberService;
  private final RefreshTokenRepository refreshTokenRepository;
  private final TokenBlacklistRepository tokenBlacklistRepository;

  public IssuedTokens loginWithPassword(String email, String password) {
    MemberService.validatePasswordLength(password);
    UserDetails userDetails =
        (UserDetails)
            authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(email, password))
                .getPrincipal();
    String role =
        userDetails.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .findFirst()
            .orElse("ROLE_USER");
    return issueTokens(userDetails.getUsername(), role);
  }

  public IssuedTokens issueTokens(String email, String role) {
    String accessToken = jwtTokenProvider.generateAccessToken(email, role);
    String refreshToken = jwtTokenProvider.generateRefreshToken(email, role);
    Duration refreshTokenTtl = jwtTokenProvider.getRefreshTokenTtl();
    refreshTokenRepository.save(email, refreshToken, refreshTokenTtl);
    return new IssuedTokens(accessToken, refreshToken, refreshTokenTtl);
  }

  // reissue
  public ReissueResult reissueAccessToken(String refreshToken) {
    validateRefreshToken(refreshToken);

    // 파싱 후 이메일 추출
    Claims claims = jwtTokenParser.parseJwt(refreshToken);
    String email = jwtTokenParser.getEmail(claims);

    // 레디스에 저장된 토큰 값 비교
    if (!refreshTokenRepository.isValid(email, refreshToken)) {
      throw new CatxiException(AuthErrorCode.REFRESH_TOKEN_MISMATCH);
    }

    // 사용자 정보 조회하여 role 가져오기
    MemberService.MemberAccount member = memberService.findAccountByEmail(email);
    if (member.status() == MemberStatus.INACTIVE
        || tokenBlacklistRepository.isUserBlacklisted(member.id().toString())) {
      throw new CatxiException(AuthErrorCode.ACCESS_FORBIDDEN);
    }

    // 토큰 생성
    String newAccessToken = jwtTokenProvider.generateAccessToken(email, member.role());
    String newRefreshToken = jwtTokenProvider.generateRefreshToken(email, member.role());

    // 토큰 회전
    Duration refreshTokenTtl = jwtTokenProvider.getRefreshTokenTtl();
    if (!refreshTokenRepository.rotate(email, refreshToken, newRefreshToken, refreshTokenTtl)) {
      throw new CatxiException(AuthErrorCode.REFRESH_TOKEN_MISMATCH);
    }

    log.info("Refresh token rotated");

    return new ReissueResult(new IssuedTokens(newAccessToken, newRefreshToken, refreshTokenTtl));
  }

  // 로그아웃 (AccessToken 블랙리스트 추가)
  public void logout(String accessToken, String refreshToken) {
    String accessTokenEmail = null;
    try {
      // AccessToken 블랙리스트에 추가
      if (accessToken != null && !accessToken.isBlank()) {
        // 토큰 유효성 검사
        if (jwtTokenParser.validateToken(accessToken)) {
          // 토큰 유효기간 계산
          Claims claims = jwtTokenParser.parseJwt(accessToken);
          Date expiration = claims.getExpiration();
          long remainTime = expiration.getTime() - System.currentTimeMillis();

          if (remainTime > 0) {
            // 블랙리스트에 추가
            tokenBlacklistRepository.addTokenToBlacklist(
                accessToken, Duration.ofMillis(remainTime));

            // 해당 사용자의 모든 refreshToken도 삭제 (보안 강화)
            String email = jwtTokenParser.getEmail(claims);
            accessTokenEmail = email;
            refreshTokenRepository.delete(email);

            log.info("Access token revoked and refresh token cleared");
          }
        }
      }

      if (refreshToken != null
          && !refreshToken.isBlank()
          && jwtTokenParser.validateToken(refreshToken)) {
        String refreshEmail = jwtTokenParser.getEmail(jwtTokenParser.parseJwt(refreshToken));
        if (accessTokenEmail == null || accessTokenEmail.equals(refreshEmail)) {
          refreshTokenRepository.deleteIfMatches(refreshEmail, refreshToken);
        }
      }
    } catch (Exception e) {
      log.warn("Logout processing failed: {}", e.getClass().getSimpleName());
    }
  }

  public void completeKakaoSignup(String email, String nickname, String studentNo) {
    memberService.completeKakaoSignup(email, nickname, studentNo);
  }

  public boolean isNNDuplicate(String nickname) {
    return memberService.isNicknameInUse(nickname);
  }

  // 리프레시토큰 검증
  private void validateRefreshToken(String refreshToken) {

    if (refreshToken == null || refreshToken.trim().isEmpty()) {
      throw new CatxiException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }
    if (!jwtTokenParser.validateToken(refreshToken)) {
      log.warn("Refresh token validation failed");
      throw new CatxiException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
    }
    log.info("Refresh token validation passed");
  }

  // 무중단 액세스 토큰 재발급 로직
  public RefreshResult zeroDownRefresh(String email, String refreshToken) {
    try {

      // 리프레시 토큰 검증
      if (refreshToken == null || !jwtTokenParser.validateToken(refreshToken)) {
        return null;
      }

      Claims refreshClaims = jwtTokenParser.parseJwt(refreshToken);
      if (!jwtTokenParser.isRefreshToken(refreshClaims)
          || !email.equals(jwtTokenParser.getEmail(refreshClaims))
          || !refreshTokenRepository.isValid(email, refreshToken)) {
        return null;
      }

      MemberService.MemberAccount member =
          memberService.findAccountByEmailOptional(email).orElse(null);
      if (member == null
          || member.status() == MemberStatus.INACTIVE
          || tokenBlacklistRepository.isUserBlacklisted(member.id().toString())) {
        return null;
      }

      // 토큰 생성
      String newAccessToken = jwtTokenProvider.generateAccessToken(email, member.role());
      String newRefreshToken = jwtTokenProvider.generateRefreshToken(email, member.role());

      // 토큰 rotate
      Duration refreshTokenTtl = jwtTokenProvider.getRefreshTokenTtl();
      if (!refreshTokenRepository.rotate(email, refreshToken, newRefreshToken, refreshTokenTtl)) {
        return null;
      }
      log.info("Token reissued");
      IssuedTokens tokens = new IssuedTokens(newAccessToken, newRefreshToken, refreshTokenTtl);
      return new RefreshResult(tokens, member);

    } catch (Exception e) {
      log.warn("Token reissue failed: {}", e.getClass().getSimpleName());
      return null;
    }
  }

  public record ReissueResult(IssuedTokens tokens) {}

  public record IssuedTokens(String accessToken, String refreshToken, Duration refreshTokenTtl) {}

  public record RefreshResult(IssuedTokens tokens, MemberService.MemberAccount member) {}
}
