package com.catxi.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(
    @NotBlank(message = "신고 사유는 필수입니다.")
        @Size(min = 10, max = 500, message = "신고 사유는 10자 이상 500자 이하로 입력해야 합니다.")
        String reason) {}
