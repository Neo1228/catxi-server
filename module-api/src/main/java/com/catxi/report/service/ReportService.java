package com.catxi.report.service;

import com.catxi.chat.service.ChatRoomService;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.common.persistence.DatabaseConstraint;
import com.catxi.member.service.MemberService;
import com.catxi.report.domain.Report;
import com.catxi.report.error.ReportErrorCode;
import com.catxi.report.event.ReportCreatedEvent;
import com.catxi.report.repository.ReportRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

  private final ReportRepository reportRepository;
  private final MemberService memberService;
  private final ChatRoomService chatRoomService;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public ReportCreated createReport(
      Long roomId, String targetUserEmail, String reporterEmail, String reason) {
    MemberService.MemberSummary reporter = memberService.findSummaryByEmail(reporterEmail);
    MemberService.MemberSummary reportedMember = memberService.findSummaryByEmail(targetUserEmail);
    validateReport(roomId, reporter, reportedMember);

    Report report =
        Report.builder()
            .roomId(roomId)
            .reporterId(reporter.id())
            .reportedMemberId(reportedMember.id())
            .reason(reason)
            .build();

    Report savedReport;
    try {
      savedReport = reportRepository.saveAndFlush(report);
    } catch (DataIntegrityViolationException exception) {
      if (DatabaseConstraint.hasName(exception, "uk_report_room_reporter_target")) {
        throw new CatxiException(ReportErrorCode.ALREADY_REPORTED);
      }
      throw exception;
    }
    eventPublisher.publishEvent(
        new ReportCreatedEvent(
            savedReport.getId(), savedReport.getRoomId(), savedReport.getCreatedTime()));

    return new ReportCreated(
        savedReport.getId(),
        reportedMember.id(),
        reportedMember.nickname(),
        savedReport.getCreatedTime());
  }

  private void validateReport(
      Long roomId,
      MemberService.MemberSummary reporter,
      MemberService.MemberSummary reportedMember) {
    if (reporter.id().equals(reportedMember.id())) {
      throw new CatxiException(ReportErrorCode.SELF_REPORT_NOT_ALLOWED);
    }

    if (!chatRoomService.hasParticipant(roomId, reporter.id())) {
      throw new CatxiException(ReportErrorCode.NOT_A_CHAT_PARTICIPANT);
    }

    if (!chatRoomService.hasParticipant(roomId, reportedMember.id())) {
      throw new CatxiException(ReportErrorCode.REPORTED_MEMBER_NOT_IN_ROOM);
    }
  }

  public record ReportCreated(
      Long reportId,
      Long reportedMemberId,
      String reportedMemberNickname,
      LocalDateTime createdAt) {}
}
