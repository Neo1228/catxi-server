package com.catxi.member.controller;

import com.catxi.auth.dto.AuthDTO.LoginRequest;
import com.catxi.auth.dto.AuthDTO.LoginResponse;
import com.catxi.auth.infra.CookieUtil;
import com.catxi.auth.service.TokenService;
import com.catxi.common.api.ApiResponse;
import com.catxi.member.dto.IdResponse;
import com.catxi.member.dto.MatchHistoryRes;
import com.catxi.member.dto.MemberProfileRes;
import com.catxi.member.dto.SignUpDTO;
import com.catxi.member.service.MatchHistoryService;
import com.catxi.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
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
  private final TokenService tokenService;

  @Operation(summary = "이메일과 비밀번호 회원가입")
  @PostMapping("/signUp")
  public ResponseEntity<IdResponse> signUp(@RequestBody @Valid SignUpDTO request) {
    Long id = memberService.signUp(request);
    return ResponseEntity.created(URI.create("/api/members/" + id)).body(new IdResponse(id));
  }

  @Operation(summary = "이메일과 비밀번호 로그인")
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @RequestBody @Valid LoginRequest request, HttpServletResponse response) {
    TokenService.IssuedTokens tokens =
        tokenService.loginWithPassword(request.email(), request.password());
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        CookieUtil.createCookie(tokens.refreshToken(), tokens.refreshTokenTtl()).toString());
    return ResponseEntity.ok(new LoginResponse(tokens.accessToken()));
  }

  @Operation(summary = "학생 이름 입력시 학번 반환")
  @GetMapping("/stdNo")
  public String getStudentNo(@RequestParam("nickname") String nickname) {
    return memberService.findStudentNoByNickname(nickname);
  }

  @Operation(summary = "회원탈퇴 API")
  @PatchMapping("/delete")
  public ApiResponse<String> delete(@AuthenticationPrincipal UserDetails userDetails) {
    memberService.delete(userDetails.getUsername());

    return ApiResponse.success("삭제 완료");
  }

  @Operation(summary = "회원 기본 정보 조회")
  @GetMapping("/")
  public ApiResponse<MemberProfileRes> getMyProfile(
      @AuthenticationPrincipal UserDetails userDetails) {
    String email = userDetails.getUsername();
    MemberProfileRes dto = memberService.getProfile(email);
    return ApiResponse.success(dto);
  }

  @Operation(summary = "이용 기록 단건 조회")
  @GetMapping("/history/{historyId}")
  public ResponseEntity<MatchHistoryRes> getMatchHistoryById(
      @PathVariable("historyId") Long historyId, @AuthenticationPrincipal UserDetails userDetails) {
    String email = userDetails.getUsername();
    MatchHistoryRes res = matchHistoryService.getHistoryById(historyId, email);
    return ResponseEntity.ok(res);
  }

  @Operation(summary = "이용 기록 페이지 조회", description = "최신 기록부터 조회하며 한 페이지는 최대 100건입니다.")
  @GetMapping("/history/all")
  public ResponseEntity<Slice<MatchHistoryRes>> getMyMatchHistoryWithScroll(
      @AuthenticationPrincipal UserDetails userDetails,
      @PageableDefault(size = 2, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    String email = userDetails.getUsername();
    Slice<MatchHistoryRes> slice = matchHistoryService.getScrollHistory(email, pageable);
    return ResponseEntity.ok(slice);
  }
}
