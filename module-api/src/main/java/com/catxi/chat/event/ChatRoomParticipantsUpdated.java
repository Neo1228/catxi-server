package com.catxi.chat.event;

import com.catxi.chat.dto.ChatParticipantSummary;
import java.util.List;

public record ChatRoomParticipantsUpdated(Long roomId, List<ChatParticipantSummary> participants) {
  public ChatRoomParticipantsUpdated {
    participants = List.copyOf(participants);
  }
}
