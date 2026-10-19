package com.catxi.member.error;

import com.catxi.common.api.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {
  DUPLICATE_STUDENT_NO(HttpStatus.BAD_REQUEST, "MEMBER400", "이미 가입된 학번입니다"),
  INVALID_NICKNAME_LENGTH(HttpStatus.BAD_REQUEST, "MEMBER401", "닉네임은 9자 이하로 입력해주세요"),
  DUPLICATE_NICKNAME(HttpStatus.BAD_REQUEST, "MEMBER402", "이미 사용중인 닉네임입니다"),
  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER403", "멤버를 찾을 수 없습니다."),
  INVALID_STUDENT_NO(HttpStatus.BAD_REQUEST, "MEMBER404", "학번은 9자리 숫자로 입력해주세요"),
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "MEMBER405", "이미 가입된 이메일입니다."),
  INVALID_PASSWORD_LENGTH(HttpStatus.BAD_REQUEST, "MEMBER406", "비밀번호는 8자 이상 72바이트 이하여야 합니다."),
  INVALID_KAKAO_EMAIL(HttpStatus.UNAUTHORIZED, "MEMBER407", "인증된 카카오 계정 이메일이 필요합니다."),
  INVALID_KAKAO_PROFILE(HttpStatus.BAD_REQUEST, "MEMBER408", "카카오 프로필 닉네임 정보가 필요합니다."),
  SIGNUP_ALREADY_COMPLETED(HttpStatus.CONFLICT, "MEMBER409", "가입 절차를 진행할 수 없는 회원 상태입니다."),

  MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "MATCH404", "이용내역을 찾을 수 없습니다."),
  MATCH_HISTORY_FORBIDDEN(HttpStatus.FORBIDDEN, "MATCH403", "본인의 이용 내역만 조회할 수 있습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
