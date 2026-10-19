package com.catxi.member.controller;

import com.catxi.common.api.ApiResponse;
import com.catxi.member.dto.MatchHistoryResponse;
import com.catxi.member.dto.MemberIdResponse;
import com.catxi.member.dto.MemberProfileResponse;
import com.catxi.member.dto.SignUpRequest;
import com.catxi.member.service.MatchHistoryService;
import com.catxi.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController {

  private final MemberService memberService;
  private final MatchHistoryService matchHistoryService;

  @Operation(summary = "이메일과 비밀번호 회원가입")
  @PostMapping("/signUp")
  public ResponseEntity<MemberIdResponse> signUp(@RequestBody @Valid SignUpRequest request) {
    Long id =
        memberService.signUp(
            request.getEmail(),
            request.getMemberName(),
            request.getPassword(),
            request.getStudentNo(),
            request.getNickname());
    return ResponseEntity.created(URI.create("/api/members/" + id)).body(new MemberIdResponse(id));
  }

  @Operation(summary = "닉네임으로 학번 조회")
  @GetMapping("/stdNo")
  public String getStudentNumber(@RequestParam("nickname") String nickname) {
    return memberService.findStudentNumberByNickname(nickname);
  }

  @Operation(summary = "회원탈퇴 API")
  @PatchMapping("/delete")
  public ApiResponse<String> delete(@AuthenticationPrincipal UserDetails userDetails) {
    memberService.delete(userDetails.getUsername());

    return ApiResponse.success("삭제 완료");
  }

  @Operation(summary = "회원 기본 정보 조회")
  @GetMapping("/")
  public ApiResponse<MemberProfileResponse> getMyProfile(
      @AuthenticationPrincipal UserDetails userDetails) {
    String email = userDetails.getUsername();
    MemberService.MemberProfile profile = memberService.getProfile(email);
    return ApiResponse.success(
        new MemberProfileResponse(profile.memberName(), profile.studentNo(), profile.matchCount()));
  }

  @Operation(summary = "이용 기록 단건 조회")
  @GetMapping("/history/{historyId}")
  public ResponseEntity<MatchHistoryResponse> getMatchHistoryById(
      @PathVariable("historyId") Long historyId, @AuthenticationPrincipal UserDetails userDetails) {
    String email = userDetails.getUsername();
    MatchHistoryService.MatchHistoryView history =
        matchHistoryService.getHistoryById(historyId, email);
    return ResponseEntity.ok(
        new MatchHistoryResponse(
            history.historyId(),
            history.matchedAt(),
            history.startPoint(),
            history.endPoint(),
            history.maskedParticipants()));
  }

  @Operation(summary = "이용 기록 페이지 조회", description = "최신 기록부터 조회하며 한 페이지는 최대 100건입니다.")
  @GetMapping("/history/all")
  public ResponseEntity<Slice<MatchHistoryResponse>> getMyMatchHistoryPage(
      @AuthenticationPrincipal UserDetails userDetails,
      @PageableDefault(size = 20) Pageable pageable) {
    String email = userDetails.getUsername();
    Slice<MatchHistoryResponse> slice =
        matchHistoryService
            .getMatchHistoryPage(email, pageable)
            .map(
                history ->
                    new MatchHistoryResponse(
                        history.historyId(),
                        history.matchedAt(),
                        history.startPoint(),
                        history.endPoint(),
                        history.maskedParticipants()));
    return ResponseEntity.ok(slice);
  }
}
