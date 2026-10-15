package com.catxi.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "spring.jwt")
public class JwtConfig {

  private String header;
  private String secret;
  private Long accessTokenValidityInSeconds;
  private Long refreshTokenValidityInSeconds;

  // Getters
  public String getHeader() {
    return header;
  }

  public String getSecret() {
    return secret;
  }

  public Long getAccessTokenValidityInSeconds() {
    return accessTokenValidityInSeconds;
  }

  public Long getRefreshTokenValidityInSeconds() {
    return refreshTokenValidityInSeconds;
  }

  // Setters
  public void setHeader(String header) {
    this.header = header;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public void setAccessTokenValidityInSeconds(Long accessTokenValidityInSeconds) {
    this.accessTokenValidityInSeconds = accessTokenValidityInSeconds;
  }

  public void setRefreshTokenValidityInSeconds(Long refreshTokenValidityInSeconds) {
    this.refreshTokenValidityInSeconds = refreshTokenValidityInSeconds;
  }
}
