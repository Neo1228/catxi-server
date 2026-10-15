package com.catxi.member.converter;

import com.catxi.member.domain.MatchHistory;
import com.catxi.member.domain.Member;
import com.catxi.member.dto.MatchHistoryRes;
import com.catxi.member.dto.MemberProfileRes;
import java.util.List;

public class MemberConverter {

  public static MemberProfileRes toMemberProfileResponse(Member member) {
    return new MemberProfileRes(
        member.getMemberName(), member.getStudentNo(), member.getMatchCount());
  }

  public static MatchHistoryRes toMatchHistoryResponse(MatchHistory history, String memberName) {
    return new MatchHistoryRes(
        history.getId(),
        history.getMatchedAt(),
        history.getStartPoint().name(),
        history.getEndPoint().name(),
        getOtherParticipants(history.getParticipants(), memberName));
  }

  // 마스킹 처리 메서드
  private static String maskName(String name) {
    if (name == null || name.isBlank()) return "";
    if (name.length() == 1) return name;
    return name.charAt(0) + "*".repeat(name.length() - 1);
  }

  private static List<String> getOtherParticipants(List<String> participants, String memberName) {
    return participants.stream()
        .filter(name -> !name.equals(memberName))
        .map(MemberConverter::maskName)
        .toList();
  }
}
