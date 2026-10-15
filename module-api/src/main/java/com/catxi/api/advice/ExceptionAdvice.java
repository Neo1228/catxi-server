package com.catxi.api.advice;

import com.catxi.common.api.ApiResponse;
import com.catxi.common.api.error.CommonErrorCode;
import com.catxi.common.api.error.ErrorCode;
import com.catxi.common.api.exception.CatxiException;
import java.util.Objects;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** API 예외를 공통 응답 형식으로 변환한다. */
@Slf4j
@RestControllerAdvice
public class ExceptionAdvice extends ResponseEntityExceptionHandler {
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex) {
    return ResponseEntity.status(CommonErrorCode.UNAUTHORIZED.getHttpStatus())
        .body(ApiResponse.failure(CommonErrorCode.UNAUTHORIZED));
  }

  @ExceptionHandler(CatxiException.class)
  public ResponseEntity<Object> handleRestApiException(CatxiException ex) {
    ErrorCode errorCode = ex.getErrorCode();
    return handleExceptionInternal(errorCode);
  }

  @Override
  protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    log.warn("지원하지 않는 HTTP 메서드 요청입니다.");
    ErrorCode errorCode = CommonErrorCode.METHOD_NOT_ALLOWED;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .headers(headers)
        .body(ApiResponse.failure(errorCode));
  }

  @Override
  public ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {

    log.warn("요청 값 검증에 실패했습니다.");
    ErrorCode errorCode = CommonErrorCode.INVALID_PARAMETER;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .headers(headers)
        .body(ApiResponse.failure(errorCode, getDefaultMessage(e)));
  }

  private static String getDefaultMessage(MethodArgumentNotValidException e) {
    StringJoiner messages = new StringJoiner(", ");
    e.getBindingResult().getAllErrors().stream()
        .limit(5)
        .map(ObjectError::getDefaultMessage)
        .filter(Objects::nonNull)
        .forEach(messages::add);
    return messages.toString();
  }

  private ResponseEntity<Object> handleExceptionInternal(final ErrorCode errorCode) {
    return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.failure(errorCode));
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (body instanceof ApiResponse<?>) {
      return super.handleExceptionInternal(ex, body, headers, status, request);
    }

    ErrorCode errorCode = commonErrorCode(status);
    if (errorCode == null) {
      return super.handleExceptionInternal(ex, body, headers, status, request);
    }
    return super.handleExceptionInternal(
        ex, ApiResponse.failure(errorCode), headers, status, request);
  }

  private ErrorCode commonErrorCode(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> CommonErrorCode.INVALID_PARAMETER;
      case 401 -> CommonErrorCode.UNAUTHORIZED;
      case 403 -> CommonErrorCode.FORBIDDEN;
      case 404 -> CommonErrorCode.ENDPOINT_NOT_FOUND;
      case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
      case 406 -> CommonErrorCode.NOT_ACCEPTABLE;
      case 413 -> CommonErrorCode.PAYLOAD_TOO_LARGE;
      case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
      case 409 -> CommonErrorCode.CONFLICT;
      case 429 -> CommonErrorCode.TOO_MANY_REQUESTS;
      case 503 -> CommonErrorCode.SERVICE_UNAVAILABLE;
      default -> status.is5xxServerError() ? CommonErrorCode.INTERNAL_SERVER_ERROR : null;
    };
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleUnexpectedException(Exception ex) {
    if (ex instanceof ErrorResponse errorResponse) {
      ErrorCode errorCode = commonErrorCode(errorResponse.getStatusCode());
      if (errorCode != null) {
        if (errorResponse.getStatusCode().is5xxServerError()) {
          log.error("요청 처리 중 서버 오류가 발생했습니다.", ex);
        }
        return ResponseEntity.status(errorResponse.getStatusCode())
            .headers(errorResponse.getHeaders())
            .body(ApiResponse.failure(errorCode));
      }
    }
    log.error("예기치 못한 서버 오류가 발생했습니다", ex);
    return handleExceptionInternal(CommonErrorCode.INTERNAL_SERVER_ERROR);
  }
}
