package com.catxi.chat.dto;

import com.catxi.common.api.CommonPageResponse;

public record ChatRoomPageResponse(Long myRoomId, CommonPageResponse<ChatRoomResponse> rooms) {}
