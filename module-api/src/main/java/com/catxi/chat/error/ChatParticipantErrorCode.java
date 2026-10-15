package com.catxi.chat.error;

import com.catxi.common.api.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatParticipantErrorCode implements ErrorCode {
  ALREADY_READY(HttpStatus.CONFLICT, "CHATPARTICIPANT409", "이미 준비 상태입니다."),
  PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "CHATPARTICIPANT404", "채팅방 참가자를 찾을 수 없습니다."),
  ALREADY_IN_ROOM(HttpStatus.CONFLICT, "CHATPARTICIPANT409", "이미 참여 중인 채팅방이 있습니다."),
  BLOCKED_FROM_ROOM(HttpStatus.FORBIDDEN, "CHATPARTICIPANT403", "강퇴된 사용자는 이 채팅방에 다시 참여할 수 없습니다."),
  CANNOT_KICK_SELF(
      HttpStatus.BAD_REQUEST, "CHATPARTICIPANT400", "방장은 자기 자신을 강퇴할 수 없습니다. 채팅방 나가기를 이용해 주세요.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
