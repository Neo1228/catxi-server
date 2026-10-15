package com.catxi.chat.repository;

import com.catxi.chat.domain.ChatRoomSummary;
import com.catxi.common.domain.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChatRoomRepositoryCustom {
  Page<ChatRoomSummary> findByLocationAndDirection(
      Location location, String point, Pageable pageable);
}
