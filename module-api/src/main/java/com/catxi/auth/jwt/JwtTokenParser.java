package com.catxi.auth.jwt;

import com.catxi.auth.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JwtTokenParser {

  private final SecretKey secretKey;
  private final JwtParser jwtParser;

  public JwtTokenParser(JwtConfig jwtConfig) {
    this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtConfig.getSecret()));
    this.jwtParser = Jwts.parser().verifyWith((SecretKey) secretKey).build();
  }

  // 유효성 검사
  public boolean validateToken(String token) {
    if (token == null || token.trim().isEmpty()) return false;
    try {
      jwtParser.parseSignedClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      log.warn("JWT validation failed: {}", e.getClass().getSimpleName());
      return false;
    }
  }

  // 파싱
  public Claims parseJwt(String token) throws ExpiredJwtException {
    try {
      return jwtParser.parseSignedClaims(token).getPayload();
    } catch (ExpiredJwtException e) {
      // 만료된 토큰의 claims도 읽어 재발급 처리로 넘깁니다.
      throw e;
    } catch (JwtException | IllegalArgumentException e) {
      log.error("JWT claims parsing failed: {}", e.getClass().getSimpleName());
      throw new IllegalArgumentException("Invalid JWT token", e);
    }
  }

  public String getEmail(Claims claims) {
    return claims.getSubject();
  }

  public String getType(Claims claims) {
    return claims.get("type", String.class);
  }

  public String getRole(Claims claims) {
    return claims.get("role", String.class);
  }

  public Date isExpired(Claims claims) {
    return claims.getExpiration();
  }

  public boolean isRefreshToken(Claims claims) {
    return "refresh".equals(claims.get("type", String.class));
  }
}
