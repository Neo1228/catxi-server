package com.catxi.auth.jwt;

import com.catxi.auth.config.JwtConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private final SecretKey secretKey;
  private final JwtConfig jwtConfig;

  public JwtTokenProvider(JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
    this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtConfig.getSecret()));
  }

  public String generateAccessToken(String email, String role) {
    return createToken("access", email, role, jwtConfig.getAccessTokenValidityInSeconds());
  }

  public String generateRefreshToken(String email, String role) {
    return createToken("refresh", email, role, jwtConfig.getRefreshTokenValidityInSeconds());
  }

  public Duration getRefreshTokenTtl() {
    return Duration.ofSeconds(jwtConfig.getRefreshTokenValidityInSeconds());
  }

  private String createToken(String type, String email, String role, Long expiredSeconds) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .subject(email)
        .claim("type", type)
        .claim("email", email)
        .claim("role", role)
        .issuedAt(new Date(now))
        .expiration(new Date(now + expiredSeconds * 1000))
        .signWith(secretKey)
        .compact();
  }
}
