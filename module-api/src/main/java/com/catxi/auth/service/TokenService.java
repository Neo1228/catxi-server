package com.catxi.auth.service;

import com.catxi.auth.infra.RefreshTokenRepository;
import com.catxi.auth.infra.TokenBlacklistRepository;
import com.catxi.auth.jwt.JwtTokenProvider;
import com.catxi.auth.jwt.JwtUtil;
import com.catxi.auth.kakao.KakaoDTO;
import com.catxi.auth.kakao.TokenDTO;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.common.persistence.DatabaseConstraint;
import com.catxi.member.domain.Member;
import com.catxi.member.domain.MemberStatus;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MemberRepository;
import com.catxi.member.service.MemberService;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

  private final AuthenticationManager authenticationManager;
  private final JwtUtil jwtUtil;
  private final JwtTokenProvider jwtTokenProvider;
  private final MemberRepository memberRepository;
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
    Claims claims = jwtUtil.parseJwt(refreshToken);
    String email = jwtUtil.getEmail(claims);

    // 레디스에 저장된 토큰 값 비교
    if (!refreshTokenRepository.isValid(email, refreshToken)) {
      throw new CatxiException(MemberErrorCode.REFRESH_TOKEN_MISMATCH);
    }

    // 사용자 정보 조회하여 role 가져오기
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    if (member.getStatus() == MemberStatus.INACTIVE
        || tokenBlacklistRepository.isUserBlacklisted(member.getId().toString())) {
      throw new CatxiException(MemberErrorCode.ACCESS_FORBIDDEN);
    }

    // 토큰 생성
    String newAccessToken = jwtTokenProvider.generateAccessToken(email, member.getRole());
    String newRefreshToken = jwtTokenProvider.generateRefreshToken(email, member.getRole());

    // 토큰 회전
    Duration refreshTokenTtl = jwtTokenProvider.getRefreshTokenTtl();
    if (!refreshTokenRepository.rotate(email, refreshToken, newRefreshToken, refreshTokenTtl)) {
      throw new CatxiException(MemberErrorCode.REFRESH_TOKEN_MISMATCH);
    }

    log.info("Refresh token rotated");

    return new ReissueResult(
        new TokenDTO.Response(newAccessToken, newRefreshToken), refreshTokenTtl);
  }

  // 로그아웃 (AccessToken 블랙리스트 추가)
  public void logout(String accessToken, String refreshToken) {
    String accessTokenEmail = null;
    try {
      // AccessToken 블랙리스트에 추가
      if (accessToken != null && !accessToken.isBlank()) {
        // 토큰 유효성 검사
        if (jwtUtil.validateToken(accessToken)) {
          // 토큰 유효기간 계산
          Claims claims = jwtUtil.parseJwt(accessToken);
          Date expiration = claims.getExpiration();
          long remainTime = expiration.getTime() - System.currentTimeMillis();

          if (remainTime > 0) {
            // 블랙리스트에 추가
            tokenBlacklistRepository.addTokenToBlacklist(
                accessToken, Duration.ofMillis(remainTime));

            // 해당 사용자의 모든 refreshToken도 삭제 (보안 강화)
            String email = jwtUtil.getEmail(claims);
            accessTokenEmail = email;
            refreshTokenRepository.delete(email);

            log.info("Access token revoked and refresh token cleared");
          }
        }
      }

      if (refreshToken != null && !refreshToken.isBlank() && jwtUtil.validateToken(refreshToken)) {
        String refreshEmail = jwtUtil.getEmail(jwtUtil.parseJwt(refreshToken));
        if (accessTokenEmail == null || accessTokenEmail.equals(refreshEmail)) {
          refreshTokenRepository.deleteIfMatches(refreshEmail, refreshToken);
        }
      }
    } catch (Exception e) {
      log.warn("Logout processing failed: {}", e.getClass().getSimpleName());
    }
  }

  @Transactional
  public void completeKakaoSignup(String email, KakaoDTO.KakaoSignupRequest dto) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    validateKakaoSignup(dto);

    member.setNickname(dto.nickname());
    member.setStudentNo(dto.studentNo());
    member.setStatus(MemberStatus.ACTIVE);
    try {
      memberRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      if (DatabaseConstraint.hasName(exception, "uk_member_nickname")) {
        throw new CatxiException(MemberErrorCode.DUPLICATE_NICKNAME);
      }
      if (DatabaseConstraint.hasName(exception, "student_no")) {
        throw new CatxiException(MemberErrorCode.DUPLICATE_STUDENT_NO);
      }
      throw exception;
    }
  }

  public boolean isNNDuplicate(String nickname) {
    return memberRepository.existsByNickname(nickname);
  }

  // 리프레시토큰 검증
  private void validateRefreshToken(String refreshToken) {

    if (refreshToken == null || refreshToken.trim().isEmpty()) {
      throw new CatxiException(MemberErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }
    if (!jwtUtil.validateToken(refreshToken)) {
      log.warn("Refresh token validation failed");
      throw new CatxiException(MemberErrorCode.REFRESH_TOKEN_EXPIRED);
    }
    log.info("Refresh token validation passed");
  }

  // 회원가입 검증
  private void validateKakaoSignup(KakaoDTO.KakaoSignupRequest dto) {
    // 닉네임 제한
    if (dto.nickname() == null || dto.nickname().length() > 9) {
      throw new CatxiException(MemberErrorCode.INVALID_NICKNAME_LENGTH);
    }
    // 닉네임 중복체크
    if (memberRepository.existsByNickname(dto.nickname())) {
      throw new CatxiException(MemberErrorCode.DUPLICATE_NICKNAME);
    }
    // 학번 정확히 9글자 숫자
    if (dto.studentNo() == null || !dto.studentNo().matches("\\d{9}")) {
      throw new CatxiException(MemberErrorCode.INVALID_STUDENT_NO);
    }
    // 학번 중복체크
    if (memberRepository.existsByStudentNo(dto.studentNo())) {
      throw new CatxiException(MemberErrorCode.DUPLICATE_STUDENT_NO);
    }
  }

  // 무중단 액세스 토큰 재발급 로직
  public RefreshResult zeroDownRefresh(String email, String refreshToken) {
    try {

      // 리프레시 토큰 검증
      if (refreshToken == null || !jwtUtil.validateToken(refreshToken)) {
        return null;
      }

      Claims refreshClaims = jwtUtil.parseJwt(refreshToken);
      if (!jwtUtil.isRefreshToken(refreshClaims)
          || !email.equals(jwtUtil.getEmail(refreshClaims))
          || !refreshTokenRepository.isValid(email, refreshToken)) {
        return null;
      }

      Member member = memberRepository.findByEmail(email).orElse(null);
      if (member == null
          || member.getStatus() == MemberStatus.INACTIVE
          || tokenBlacklistRepository.isUserBlacklisted(member.getId().toString())) {
        return null;
      }

      // 토큰 생성
      String newAccessToken = jwtTokenProvider.generateAccessToken(email, member.getRole());
      String newRefreshToken = jwtTokenProvider.generateRefreshToken(email, member.getRole());

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

  public record ReissueResult(TokenDTO.Response tokens, Duration refreshTokenTtl) {}

  public record IssuedTokens(String accessToken, String refreshToken, Duration refreshTokenTtl) {}

  public record RefreshResult(IssuedTokens tokens, Member member) {}
}
