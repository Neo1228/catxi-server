package com.catxi.auth.kakao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "catxi.auth", name = "mode", havingValue = "kakao")
@RequiredArgsConstructor
@Slf4j
public class KakaoOAuthClient {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  @Value("${KAKAO_CLIENT_ID:}")
  private String client;

  @Value("${KAKAO_CLIENT_SECRET:}")
  private String clientSecret;

  @Value("${KAKAO_REDIRECT_URI:}")
  private String redirect;

  @jakarta.annotation.PostConstruct
  void validateConfiguration() {
    if (client.isBlank() || clientSecret.isBlank() || redirect.isBlank()) {
      throw new IllegalStateException("Kakao 인증이 켜져 있으면 OAuth 환경 변수 세 가지를 설정해야 합니다");
    }
  }

  public KakaoTokenResponse requestToken(String accessCode) {
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "authorization_code");
    params.add("client_id", client);
    params.add("client_secret", clientSecret);
    params.add("redirect_uri", redirect);
    params.add("code", accessCode);

    try {
      String response =
          restClient
              .post()
              .uri("https://kauth.kakao.com/oauth/token")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(params)
              .retrieve()
              .body(String.class);

      if (response == null) {
        throw new IllegalStateException("Kakao token response was empty");
      }
      return objectMapper.readValue(response, KakaoTokenResponse.class);
    } catch (RestClientException | JacksonException e) {
      log.warn("Kakao token request failed: {}", e.getClass().getSimpleName());
      throw new IllegalStateException("Kakao token request failed", e);
    }
  }

  public KakaoProfileResponse requestProfile(KakaoTokenResponse kakaoToken) {
    try {
      String response =
          restClient
              .get()
              .uri("https://kapi.kakao.com/v2/user/me")
              .headers(headers -> headers.setBearerAuth(kakaoToken.accessToken()))
              .retrieve()
              .body(String.class);

      if (response == null) {
        throw new IllegalStateException("Kakao profile response was empty");
      }
      return objectMapper.readValue(response, KakaoProfileResponse.class);
    } catch (RestClientException | JacksonException e) {
      log.warn("Kakao profile request failed: {}", e.getClass().getSimpleName());
      throw new IllegalStateException("Kakao profile request failed", e);
    }
  }
}
