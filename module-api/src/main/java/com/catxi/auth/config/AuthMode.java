package com.catxi.auth.config;

import java.util.Locale;

public enum AuthMode {
  KAKAO,
  LOCAL;

  public static AuthMode parse(String value) {
    if (value == null) {
      throw new IllegalArgumentException("CATXI_AUTH_MODE 값은 kakao 또는 local이어야 합니다.");
    }

    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "kakao" -> KAKAO;
      case "local" -> LOCAL;
      default -> throw new IllegalArgumentException("CATXI_AUTH_MODE 값은 kakao 또는 local이어야 합니다.");
    };
  }
}
