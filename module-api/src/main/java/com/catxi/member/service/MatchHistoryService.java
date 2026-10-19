package com.catxi.member.service;

import com.catxi.common.api.exception.CatxiException;
import com.catxi.common.domain.MeetingPoint;
import com.catxi.member.domain.MatchHistory;
import com.catxi.member.domain.Member;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MatchHistoryRepository;
import com.catxi.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchHistoryService {

  private static final int MAX_HISTORY_PAGE_SIZE = 100;

  private final MatchHistoryRepository matchHistoryRepository;
  private final MemberRepository memberRepository;

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordMatchedRoom(
      Long roomId,
      MeetingPoint startPoint,
      MeetingPoint endPoint,
      LocalDateTime matchedAt,
      List<MatchParticipant> participants) {
    if (participants.isEmpty()) {
      return;
    }

    List<String> participantNames = participants.stream().map(MatchParticipant::nickname).toList();
    LocalDateTime createdAt = LocalDateTime.now();

    for (MatchParticipant participant : participants) {
      if (matchHistoryRepository.existsByRoomIdAndUser_Id(roomId, participant.memberId())) {
        continue;
      }
      matchHistoryRepository.save(
          MatchHistory.builder()
              .roomId(roomId)
              .user(memberRepository.getReferenceById(participant.memberId()))
              .startPoint(startPoint)
              .endPoint(endPoint)
              .participants(participantNames)
              .createdAt(createdAt)
              .matchedAt(matchedAt)
              .build());
      memberRepository.incrementMatchCount(participant.memberId());
    }
  }

  public MatchHistoryView getHistoryById(Long historyId, String email) {
    Member user =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    MatchHistory history =
        matchHistoryRepository
            .findById(historyId)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MATCH_NOT_FOUND));

    if (!history.getUser().equals(user)) {
      throw new CatxiException(MemberErrorCode.MATCH_HISTORY_FORBIDDEN);
    }
    return toView(history, user.getMemberName());
  }

  public Slice<MatchHistoryView> getScrollHistory(String email, Pageable pageable) {
    Member user =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    int pageNumber = pageable.isPaged() ? pageable.getPageNumber() : 0;
    int requestedSize = pageable.isPaged() ? pageable.getPageSize() : MAX_HISTORY_PAGE_SIZE;
    Pageable boundedPageable =
        PageRequest.of(
            pageNumber,
            Math.min(Math.max(requestedSize, 1), MAX_HISTORY_PAGE_SIZE),
            Sort.unsorted());
    String memberName = user.getMemberName();
    Slice<MatchHistory> histories =
        matchHistoryRepository.findHistoriesByUserOrParticipant(email, memberName, boundedPageable);

    return histories.map(history -> toView(history, memberName));
  }

  private MatchHistoryView toView(MatchHistory history, String memberName) {
    List<String> maskedParticipants =
        history.getParticipants().stream()
            .filter(name -> !Objects.equals(name, memberName))
            .map(MatchHistoryService::maskName)
            .toList();
    return new MatchHistoryView(
        history.getId(),
        history.getMatchedAt(),
        history.getStartPoint().name(),
        history.getEndPoint().name(),
        maskedParticipants);
  }

  private static String maskName(String name) {
    if (name == null || name.isBlank()) {
      return "";
    }
    if (name.length() == 1) {
      return name;
    }
    return name.charAt(0) + "*".repeat(name.length() - 1);
  }

  public record MatchHistoryView(
      Long historyId,
      LocalDateTime matchedAt,
      String startPoint,
      String endPoint,
      List<String> maskedParticipants) {}

  public record MatchParticipant(Long memberId, String nickname) {}
}
