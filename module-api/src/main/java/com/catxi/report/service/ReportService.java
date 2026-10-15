package com.catxi.report.service;

import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.error.ChatRoomErrorCode;
import com.catxi.chat.repository.ChatParticipantRepository;
import com.catxi.chat.repository.ChatRoomRepository;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.common.persistence.DatabaseConstraint;
import com.catxi.member.domain.Member;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MemberRepository;
import com.catxi.report.domain.Report;
import com.catxi.report.dto.ReportCreateReq;
import com.catxi.report.dto.ReportCreateRes;
import com.catxi.report.error.ReportErrorCode;
import com.catxi.report.event.ReportCreatedEvent;
import com.catxi.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

  private final ReportRepository reportRepository;
  private final MemberRepository memberRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final ChatParticipantRepository chatParticipantRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Value("${discord.reports.enabled}")
  private boolean discordReportsEnabled;

  @Transactional
  public ReportCreateRes createReport(
      Long roomId, String targetUserEmail, String reporterEmail, ReportCreateReq req) {
    Member reporter =
        memberRepository
            .findByEmail(reporterEmail)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    ChatRoom chatRoom =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    Member reportedMember =
        memberRepository
            .findByEmail(targetUserEmail)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    validateReport(chatRoom, reporter, reportedMember);

    Report report =
        Report.builder()
            .roomId(roomId)
            .reporter(reporter)
            .reportedMember(reportedMember)
            .reason(req.reason())
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
    if (discordReportsEnabled) {
      eventPublisher.publishEvent(
          new ReportCreatedEvent(
              savedReport.getId(), savedReport.getRoomId(), savedReport.getCreatedTime()));
    }

    return new ReportCreateRes(
        savedReport.getId(),
        reportedMember.getId(),
        reportedMember.getNickname(),
        savedReport.getCreatedTime());
  }

  private void validateReport(ChatRoom room, Member reporter, Member reportedMember) {
    if (reporter.getId().equals(reportedMember.getId())) {
      throw new CatxiException(ReportErrorCode.SELF_REPORT_NOT_ALLOWED);
    }

    if (!chatParticipantRepository.existsByChatRoomAndMember(room, reporter)) {
      throw new CatxiException(ReportErrorCode.NOT_A_CHAT_PARTICIPANT);
    }

    if (!chatParticipantRepository.existsByChatRoomAndMember(room, reportedMember)) {
      throw new CatxiException(ReportErrorCode.REPORTED_MEMBER_NOT_IN_ROOM);
    }

    reportRepository
        .findDuplicateReport(room.getRoomId(), reporter.getId(), reportedMember.getId())
        .ifPresent(
            report -> {
              throw new CatxiException(ReportErrorCode.ALREADY_REPORTED);
            });
  }
}
