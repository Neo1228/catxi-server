package com.catxi.notification.fcm.error;

import com.catxi.common.api.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FcmErrorCode implements ErrorCode {
  FCM_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "FCM400", "FCM 토큰을 찾을 수 없습니다."),
  FCM_TOKEN_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FCM401", "FCM 토큰 업데이트에 실패했습니다."),
  FCM_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "FCM402", "FCM 서비스를 사용할 수 없습니다."),
  FCM_NOTIFICATION_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FCM403", "FCM 알림 발송에 실패했습니다."),
  INVALID_FCM_TOKEN_FORMAT(HttpStatus.BAD_REQUEST, "FCM404", "올바르지 않은 FCM 토큰 형식입니다."),
  FCM_TOKEN_DELETION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FCM405", "FCM 토큰 삭제에 실패했습니다."),
  FCM_OUTBOX_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FCM406", "FCM Outbox 저장에 실패했습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
