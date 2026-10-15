package com.catxi.chat.error;

import com.catxi.common.api.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatRoomErrorCode implements ErrorCode {
  CHATROOM_READY_LOCKED(HttpStatus.CONFLICT, "CHATROOM409", "채팅방이 준비 요청 상태입니다."),
  CHATROOM_NOT_READY_LOCKED(HttpStatus.CONFLICT, "CHATROOM409", "채팅방이 준비 요청 상태가 아닙니다."),
  CHATROOM_NOT_WAITING(HttpStatus.CONFLICT, "CHATROOM409", "채팅방이 대기 상태가 아닙니다."),
  CHATROOM_NOT_MATCHED(HttpStatus.CONFLICT, "CHATROOM409", "채팅방이 매칭 상태가 아닙니다."),
  CHATROOM_FULL(HttpStatus.CONFLICT, "CHATROOM409", "채팅방 정원이 가득 찼습니다."),
  CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHATROOM404", "채팅방을 찾을 수 없습니다."),
  NOT_OWNED_CHATROOM(HttpStatus.FORBIDDEN, "CHATROOM403", "본인 소유의 채팅방이 아닙니다."),
  INVALID_CHATROOM_PARAMETER(HttpStatus.BAD_REQUEST, "CHATROOM400", "채팅방 요청 데이터가 올바르지 않습니다."),
  CHAT_HISTORY_PAGINATION_REQUIRED(
      HttpStatus.BAD_REQUEST, "CHATROOM400", "채팅 이력이 많습니다. 페이지 조회 API를 사용해 주세요."),
  NOT_HOST(HttpStatus.FORBIDDEN, "CHATROOM403", "방장만 실행할 수 있습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
