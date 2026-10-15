package com.catxi.auth.kakao;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class KakaoDTO {

  // 카카오 액세스 토큰
  public record KakaoToken(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("expires_in") int expiresIn,
      String scope,
      @JsonProperty("refresh_token_expires_in") int refreshTokenExpiresIn) {}

  // 카카오 사용자 정보 응답
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KakaoProfile(
      Long id,
      @JsonProperty("connected_at") String connectedAt,
      @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
    // 카카오 계정에서 추출할 내용
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(
        String email,
        @JsonProperty("is_email_valid") Boolean emailValid,
        @JsonProperty("is_email_verified") Boolean emailVerified,
        Profile profile) {
      @JsonIgnoreProperties(ignoreUnknown = true)
      public record Profile(String nickname) {}
    }
  }

  // 추가 회원가입 폼용
  public record KakaoSignupRequest(
      @NotBlank String nickname, @JsonAlias("StudentNo") @NotBlank String studentNo) {}
}
