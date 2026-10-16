package com.catxi.report.controller;

import com.catxi.common.api.ApiResponse;
import com.catxi.report.dto.ReportCreateRequest;
import com.catxi.report.dto.ReportCreateResponse;
import com.catxi.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReportController {

  private final ReportService reportService;

  public ReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  @PostMapping("/rooms/{roomId}/report/{targetUserEmail}")
  public ResponseEntity<ApiResponse<ReportCreateResponse>> reportUser(
      @PathVariable Long roomId,
      @PathVariable String targetUserEmail,
      @Valid @RequestBody ReportCreateRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {

    String reporterEmail = userDetails.getUsername();
    ReportService.ReportCreated report =
        reportService.createReport(roomId, targetUserEmail, reporterEmail, request.reason());
    ReportCreateResponse response =
        new ReportCreateResponse(
            report.reportId(),
            report.reportedMemberId(),
            report.reportedMemberNickname(),
            report.createdAt());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
  }
}
