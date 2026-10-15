package com.catxi.report.controller;

import com.catxi.auth.security.CustomUserDetails;
import com.catxi.common.api.ApiResponse;
import com.catxi.report.dto.ReportCreateReq;
import com.catxi.report.dto.ReportCreateRes;
import com.catxi.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReportController {

  private final ReportService reportService;

  public ReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  @PostMapping("/rooms/{roomId}/report/{targetUserEmail}")
  public ResponseEntity<ApiResponse<ReportCreateRes>> reportUser(
      @PathVariable Long roomId,
      @PathVariable String targetUserEmail,
      @Valid @RequestBody ReportCreateReq request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    String reporterEmail = userDetails.getUsername();
    ReportCreateRes response =
        reportService.createReport(roomId, targetUserEmail, reporterEmail, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
  }
}
