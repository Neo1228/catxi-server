package com.catxi.report.error;

import com.catxi.common.api.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {
  SELF_REPORT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "REPORT402", "자기 자신을 신고할 수 없습니다."),
  NOT_A_CHAT_PARTICIPANT(HttpStatus.FORBIDDEN, "REPORT403", "채팅방 참가자만 신고할 수 있습니다."),
  REPORTED_MEMBER_NOT_IN_ROOM(HttpStatus.BAD_REQUEST, "REPORT404", "신고 대상이 해당 채팅방에 없습니다."),
  ALREADY_REPORTED(HttpStatus.CONFLICT, "REPORT409", "해당 사용자에 대한 신고가 이미 접수되었습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
