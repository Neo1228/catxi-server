package com.catxi.chat.realtime.message;

import com.catxi.chat.dto.ChatParticipantSummary;
import java.util.List;

public record ParticipantsUpdateMessage(Long roomId, List<ChatParticipantSummary> participants) {}
