package com.catxi.auth.controller;

import com.catxi.auth.dto.AccessTokenResponse;
import com.catxi.auth.dto.AuthDTO.LoginRequest;
import com.catxi.auth.dto.AuthDTO.LoginResponse;
import com.catxi.auth.dto.AuthDTO;
import com.catxi.auth.security.RefreshTokenCookie;
import com.catxi.auth.service.OAuthLoginService;
import com.catxi.auth.service.TokenService;
import com.catxi.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class OAuthController {

  private final ObjectProvider<OAuthLoginService> oAuthLoginServiceProvider;
  private final TokenService tokenService;

  @Operation(summary = "인가코드 로그인", description = "카카오에서 인가코드를 받아와 JWT를 발급해줍니다.")
  @PostMapping("/api/members/login")
  public ResponseEntity<LoginResponse> passwordLogin(
      @RequestBody @Valid LoginRequest request, HttpServletResponse response) {
    TokenService.IssuedTokens tokens =
        tokenService.loginWithPassword(request.email(), request.password());
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        RefreshTokenCookie.createCookie(tokens.refreshToken(), tokens.refreshTokenTtl())
            .toString());
    return ResponseEntity.ok(new LoginResponse(tokens.accessToken()));
  }

  @GetMapping("/api/auth/kakao/callback")
  public ResponseEntity<Void> kakaoCallback(@RequestParam("code") String code) {
    requireKakaoAuth();

    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create("https://catxi-university-taxi-b0936.web.app/home"));
    return new ResponseEntity<>(headers, HttpStatus.FOUND);
  }

  @GetMapping("/api/auth/login/kakao")
  public ApiResponse<?> kakaoLogin(
      @RequestParam("code") String accessCode, HttpServletResponse response) {

    OAuthLoginService.LoginResult result = requireKakaoAuth().kakaoLoginProcess(accessCode);
    response.setHeader("access", result.tokens().accessToken());
    response.setHeader(
        HttpHeaders.SET_COOKIE,
        RefreshTokenCookie.createCookie(
                result.tokens().refreshToken(), result.tokens().refreshTokenTtl())
            .toString());
    response.setHeader("isNewUser", Boolean.toString(result.newUser()));
    return ApiResponse.success(result.newUser() ? "isNewUser" : "로그인 성공");
  }

  // 추가 회원가입 단계
  @Operation(summary = "추가 회원가입", description = "멤버의 닉네임, 학번을 받아오고 MemberStatus를 PENDING으로 변환해줍니다")
  @PatchMapping("/api/auth/signUp/catxi")
  public ApiResponse<?> completeSignup(
      @RequestBody @Valid AuthDTO.KakaoSignupRequest dto, Principal principal) {

    tokenService.completeKakaoSignup(principal.getName(), dto.nickname(), dto.studentNo());
    return ApiResponse.success("추가 회원정보 등록 완료");
  }

  @Operation(summary = "닉네임 중복 조회")
  @GetMapping("/api/auth/signUp/catxi/checkNN")
  public ApiResponse<Boolean> checkNN(@RequestParam("nickname") String nickname) {
    boolean isDuplicate = tokenService.isNNDuplicate(nickname);
    return ApiResponse.success(isDuplicate);
  }

  // Reissue
  @Operation(
      summary = "액세스 토큰 재발급",
      description = "리프레시 토큰을 검증한 뒤 액세스 토큰은 본문으로, 새 리프레시 토큰은 보안 쿠키로 전달합니다.")
  @PostMapping("/api/auth/reissue")
  public ApiResponse<AccessTokenResponse> reissue(
      @CookieValue(name = "refresh", required = false) String refreshToken,
      HttpServletResponse response) {

    TokenService.ReissueResult result = tokenService.reissueAccessToken(refreshToken);
    response.setHeader(
        HttpHeaders.SET_COOKIE,
        RefreshTokenCookie.createCookie(
                result.tokens().refreshToken(), result.tokens().refreshTokenTtl())
            .toString());
    return ApiResponse.success(new AccessTokenResponse(result.tokens().accessToken()));
  }

  // 로그아웃
  @Operation(summary = "사용자 로그아웃", description = "사용자의 액세스 토큰을 blacklist에 등록해, JWT를 무효화시킵니다")
  @PostMapping("/api/auth/logout")
  public ApiResponse<?> logout(
      HttpServletRequest request,
      @CookieValue(name = "refresh", required = false) String refreshToken,
      HttpServletResponse response) {

    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    String accessToken =
        authorization != null && authorization.startsWith("Bearer ")
            ? authorization.substring("Bearer ".length())
            : null;
    tokenService.logout(accessToken, refreshToken);
    response.addHeader(HttpHeaders.SET_COOKIE, RefreshTokenCookie.deleteCookie().toString());
    return ApiResponse.success("로그아웃 완료");
  }

  private OAuthLoginService requireKakaoAuth() {
    OAuthLoginService service = oAuthLoginServiceProvider.getIfAvailable();
    if (service == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return service;
  }

  // TODO: 회원 탈퇴

}
