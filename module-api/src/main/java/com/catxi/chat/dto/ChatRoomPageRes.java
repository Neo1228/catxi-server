package com.catxi.chat.dto;

import com.catxi.common.api.CommonPageResponse;
import org.springframework.data.domain.Page;

public record ChatRoomPageRes(Long myRoomId, CommonPageResponse<ChatRoomRes> rooms) {
  public static ChatRoomPageRes of(Page<ChatRoomRes> page, Long myRoomId) {
    return new ChatRoomPageRes(myRoomId, CommonPageResponse.of(page));
  }
}
