package com.catxi.member.service;

import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.converter.MemberConverter;
import com.catxi.member.domain.MatchHistory;
import com.catxi.member.domain.Member;
import com.catxi.member.dto.MatchHistoryRes;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MatchHistoryRepository;
import com.catxi.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchHistoryService {

  private static final int MAX_HISTORY_PAGE_SIZE = 100;

  private final MatchHistoryRepository matchHistoryRepository;
  private final MemberRepository memberRepository;

  public MatchHistoryRes getHistoryById(Long historyId, String email) {
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
    return MemberConverter.toMatchHistoryResponse(history, user.getMemberName());
  }

  public Slice<MatchHistoryRes> getScrollHistory(String email, Pageable pageable) {
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

    return histories.map(history -> MemberConverter.toMatchHistoryResponse(history, memberName));
  }
}
