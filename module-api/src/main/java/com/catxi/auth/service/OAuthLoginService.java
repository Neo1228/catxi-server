package com.catxi.auth.service;

import com.catxi.auth.infra.CodeCache;
import com.catxi.auth.kakao.KakaoDTO;
import com.catxi.auth.kakao.KakaoUtil;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.domain.Member;
import com.catxi.member.domain.MemberStatus;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "catxi.auth", name = "mode", havingValue = "kakao")
@RequiredArgsConstructor
@Slf4j
public class OAuthLoginService {

  private final KakaoUtil kakaoUtil;
  private final MemberRepository memberRepository;
  private final CodeCache codeCache;
  private final TokenService tokenService;

  public LoginResult kakaoLoginProcess(String accessCode) {
    if (accessCode == null || accessCode.isBlank()) {
      throw new CatxiException(MemberErrorCode.ACCESS_EXPIRED);
    }

    // Redis 선점 성공 여부로 여러 인스턴스에서 인가 코드 재사용을 차단합니다.
    if (!codeCache.markIfUnused(accessCode)) {
      log.warn("Duplicate OAuth authorization code rejected");
      throw new CatxiException(MemberErrorCode.DUPLICATE_AUTHORIZE_CODE);
    }
    try {
      Member member = oAuthLogin(accessCode);
      return loginProcess(member);
    } catch (CatxiException exception) {
      throw exception;
    } catch (Exception e) {
      log.error("Kakao login failed: {}", e.getClass().getSimpleName());
      throw new CatxiException(MemberErrorCode.ACCESS_EXPIRED);
    }
  }

  private Member oAuthLogin(String accessCode) {

    // 카카오 토큰 요청
    KakaoDTO.KakaoToken kakaoToken = kakaoUtil.requestToken(accessCode);
    // 사용자 정보 요청
    KakaoDTO.KakaoProfile kakaoProfile = kakaoUtil.requestProfile(kakaoToken);

    var kakaoAccount = kakaoProfile.kakaoAccount();
    if (kakaoAccount == null
        || kakaoAccount.email() == null
        || kakaoAccount.email().isBlank()
        || !Boolean.TRUE.equals(kakaoAccount.emailValid())
        || !Boolean.TRUE.equals(kakaoAccount.emailVerified())) {
      throw new CatxiException(MemberErrorCode.INVALID_KAKAO_EMAIL);
    }

    String email = kakaoAccount.email();
    Member user = memberRepository.findByEmail(email).orElseGet(() -> createNewUser(kakaoProfile));

    validateNewUser(user);

    return user;
  }

  private Member createNewUser(KakaoDTO.KakaoProfile kakaoProfile) {
    String email = kakaoProfile.kakaoAccount().email();
    var profile = kakaoProfile.kakaoAccount().profile();
    if (profile == null || profile.nickname() == null || profile.nickname().isBlank()) {
      throw new CatxiException(MemberErrorCode.INVALID_KAKAO_PROFILE);
    }
    String name = profile.nickname();

    Member newUser =
        Member.builder()
            .email(email)
            .memberName(name)
            .password("NO_PASSWORD")
            .matchCount(0)
            .role("ROLE_USER")
            .status(MemberStatus.PENDING)
            .build();

    try {
      return memberRepository.save(newUser);
    } catch (DataIntegrityViolationException exception) {
      return memberRepository.findByEmail(email).orElseThrow(() -> exception);
    }
  }

  private LoginResult loginProcess(Member user) {
    TokenService.IssuedTokens tokens = tokenService.issueTokens(user.getEmail(), user.getRole());
    return new LoginResult(tokens, user.getStatus() == MemberStatus.PENDING);
  }

  // 회원 Status 점검
  private void validateNewUser(Member user) {
    if (user.getStatus() == MemberStatus.INACTIVE) {
      throw new CatxiException(MemberErrorCode.ACCESS_FORBIDDEN);
    }
  }

  public record LoginResult(TokenService.IssuedTokens tokens, boolean newUser) {}
}
