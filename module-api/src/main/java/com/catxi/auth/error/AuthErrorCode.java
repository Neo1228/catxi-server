package com.catxi.auth.error;

import com.catxi.common.api.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "ACCESS400", "유효하지 않은 토큰입니다"),
  ACCESS_EXPIRED(HttpStatus.UNAUTHORIZED, "ACCESS401", "액세스 토큰이 만료되었습니다"),
  DUPLICATE_AUTHORIZE_CODE(HttpStatus.UNAUTHORIZED, "ACCESS402", "인가 코드 중복 사용"),
  ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "ACCESS403", "탈퇴한 회원입니다."),
  REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "ACCESS404", "리프레시 토큰이 만료되었습니다."),
  REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "ACCESS405", "리프레시 토큰이 존재하지 않습니다."),
  REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "ACCESS406", "리프레시 토큰이 일치하지 않습니다."),
  TOKEN_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "ACCESS407", "토큰 서명이 올바르지 않습니다."),
  TOKEN_BLACKLISTED(HttpStatus.UNAUTHORIZED, "ACCESS408", "블랙리스트에 등록된 토큰입니다."),
  USER_BLACKLISTED(HttpStatus.FORBIDDEN, "ACCESS409", "블랙리스트에 등록된 사용자입니다."),
  TOKEN_CLAIM_INVALID(HttpStatus.UNAUTHORIZED, "ACCESS410", "토큰 클레임 정보가 유효하지 않습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;

  @Override
  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
