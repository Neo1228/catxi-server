package com.catxi.chat.dto;

import java.util.List;

public record ChatRoomParticipantsUpdated(Long roomId, List<ParticipantBrief> participants) {
  public ChatRoomParticipantsUpdated {
    participants = List.copyOf(participants);
  }
}
