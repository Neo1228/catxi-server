package com.catxi.auth.kakao;

public record TokenDTO() {

  public record Request() {}

  public record Response(String accessToken, String refreshToken) {}
}
