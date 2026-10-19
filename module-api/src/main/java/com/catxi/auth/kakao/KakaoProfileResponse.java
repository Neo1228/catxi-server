package com.catxi.auth.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoProfileResponse(
    Long id,
    @JsonProperty("connected_at") String connectedAt,
    @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

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
