package com.catxi.member.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record MatchHistoryRes(
    Long historyId,
    LocalDateTime matchedAt,
    String startPoint,
    String endPoint,
    // 기존 클라이언트 응답 계약과 호환하려고 JSON 필드명은 유지한다.
    @JsonProperty("maskedFellas") List<String> maskedParticipants) {}
