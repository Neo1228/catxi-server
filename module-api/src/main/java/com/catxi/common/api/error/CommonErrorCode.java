package com.catxi.common.api.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

  // COMMON 4XX
  INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON400", "파라미터가 올바르지 않습니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "요청 권한이 없습니다."),
  ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "요청한 경로를 찾을 수 없습니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON405", "허용되지 않는 HTTP Method입니다."),
  CONFLICT(HttpStatus.CONFLICT, "COMMON409", "요청이 현재 상태와 충돌합니다."),
  NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "COMMON406", "요청한 응답 형식을 제공할 수 없습니다."),
  PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "COMMON413", "요청 본문이 허용 크기를 초과했습니다."),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON415", "지원하지 않는 요청 형식입니다."),
  TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COMMON429", "요청이 너무 많습니다."),

  // COMMON 5XX
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 내부 오류입니다."),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "COMMON503", "서비스를 일시적으로 사용할 수 없습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
