package com.catxi.chat.error;

import com.catxi.common.api.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatMessageErrorCode implements ErrorCode {
  IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "CHATMESSAGE409", "같은 요청 식별자를 다른 메시지에 사용할 수 없습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
