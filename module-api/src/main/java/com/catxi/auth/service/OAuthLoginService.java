package com.catxi.auth.service;

import com.catxi.auth.error.AuthErrorCode;
import com.catxi.auth.infrastructure.OAuthCodeCache;
import com.catxi.auth.kakao.KakaoProfileResponse;
import com.catxi.auth.kakao.KakaoTokenResponse;
import com.catxi.auth.kakao.KakaoOAuthClient;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.domain.MemberStatus;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "catxi.auth", name = "mode", havingValue = "kakao")
@RequiredArgsConstructor
@Slf4j
public class OAuthLoginService {

  private final KakaoOAuthClient kakaoOAuthClient;
  private final MemberService memberService;
  private final OAuthCodeCache oauthCodeCache;
  private final TokenService tokenService;

  public LoginResult kakaoLoginProcess(String accessCode) {
    if (accessCode == null || accessCode.isBlank()) {
      throw new CatxiException(AuthErrorCode.ACCESS_EXPIRED);
    }

    // Redis 선점 성공 여부로 여러 인스턴스에서 인가 코드 재사용을 차단합니다.
    if (!oauthCodeCache.markIfUnused(accessCode)) {
      log.warn("Duplicate OAuth authorization code rejected");
      throw new CatxiException(AuthErrorCode.DUPLICATE_AUTHORIZE_CODE);
    }
    try {
      MemberService.MemberAccount member = oAuthLogin(accessCode);
      return loginProcess(member);
    } catch (CatxiException exception) {
      throw exception;
    } catch (Exception e) {
      log.error("Kakao login failed: {}", e.getClass().getSimpleName());
      throw new CatxiException(AuthErrorCode.ACCESS_EXPIRED);
    }
  }

  private MemberService.MemberAccount oAuthLogin(String accessCode) {

    // 카카오 토큰 요청
    KakaoTokenResponse kakaoToken = kakaoOAuthClient.requestToken(accessCode);
    // 사용자 정보 요청
    KakaoProfileResponse kakaoProfile = kakaoOAuthClient.requestProfile(kakaoToken);

    var kakaoAccount = kakaoProfile.kakaoAccount();
    if (kakaoAccount == null
        || kakaoAccount.email() == null
        || kakaoAccount.email().isBlank()
        || !Boolean.TRUE.equals(kakaoAccount.emailValid())
        || !Boolean.TRUE.equals(kakaoAccount.emailVerified())) {
      throw new CatxiException(MemberErrorCode.INVALID_KAKAO_EMAIL);
    }

    String email = kakaoAccount.email();
    var profile = kakaoAccount.profile();
    if (profile == null || profile.nickname() == null || profile.nickname().isBlank()) {
      throw new CatxiException(MemberErrorCode.INVALID_KAKAO_PROFILE);
    }
    return memberService.findOrCreateKakaoMember(email, profile.nickname());
  }

  private LoginResult loginProcess(MemberService.MemberAccount user) {
    if (user.status() == MemberStatus.INACTIVE) {
      throw new CatxiException(AuthErrorCode.ACCESS_FORBIDDEN);
    }

    TokenService.IssuedTokens tokens = tokenService.issueTokens(user.email(), user.role());
    return new LoginResult(tokens, user.status() == MemberStatus.PENDING);
  }

  public record LoginResult(TokenService.IssuedTokens tokens, boolean newUser) {}
}
