package com.catxi.chat.repository;

import com.catxi.common.domain.MeetingPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChatRoomRepositoryCustom {
  Page<ChatRoomSummary> findByMeetingPointAndDirection(
      MeetingPoint meetingPoint, String direction, Pageable pageable);
}
