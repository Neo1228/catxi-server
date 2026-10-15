package com.catxi.auth.infra;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

  private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh";

  public static ResponseCookie createCookie(String token, Duration ttl) {

    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, token)
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(ttl)
        .sameSite("None")
        .build();
  }

  public static ResponseCookie deleteCookie() {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(0)
        .sameSite("None")
        .build();
  }
}
