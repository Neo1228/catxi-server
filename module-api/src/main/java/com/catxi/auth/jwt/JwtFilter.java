package com.catxi.auth.jwt;

import com.catxi.auth.config.AuthMode;
import com.catxi.auth.error.AuthErrorCode;
import com.catxi.auth.redis.TokenBlacklistRepository;
import com.catxi.auth.security.MemberUserDetails;
import com.catxi.auth.security.RefreshTokenCookie;
import com.catxi.auth.service.TokenService;
import com.catxi.common.api.ApiResponse;
import com.catxi.common.api.error.CommonErrorCode;
import com.catxi.common.api.error.ErrorCode;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.domain.MemberStatus;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.service.MemberService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

  private final JwtTokenParser jwtTokenParser;
  private final TokenService tokenService;
  private final MemberService memberService;
  private final TokenBlacklistRepository tokenBlacklistRepository;
  private final AuthMode authMode;
  private final ObjectMapper objectMapper;

  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String uri = request.getRequestURI();

    if (uri.startsWith("/connect")
        || uri.equals("/api/auth/login/kakao")
        || uri.equals("/api/auth/reissue")
        || (authMode == AuthMode.LOCAL
            && (uri.equals("/api/members/signUp") || uri.equals("/api/members/login")))
        || uri.startsWith("/webjars/")
        || uri.startsWith("/actuator")) {
      filterChain.doFilter(request, response);
      return;
    }

    String authorization = request.getHeader(AUTH_HEADER);
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      log.info("Authorization 헤더가 없거나 Bearer 토큰이 아닙니다");
      filterChain.doFilter(request, response);
      return;
    }

    try {
      String accessToken = authorization.substring(BEARER_PREFIX.length());
      Claims claims = null;
      boolean refreshedAccessToken = false;
      try {
        claims = jwtTokenParser.parseJwt(accessToken);
      } catch (ExpiredJwtException e) {
        if (!"access".equals(jwtTokenParser.getType(e.getClaims()))) {
          throw new CatxiException(AuthErrorCode.INVALID_TOKEN);
        }
        if (tokenBlacklistRepository.isTokenBlacklisted(accessToken)) {
          throw new CatxiException(AuthErrorCode.ACCESS_FORBIDDEN);
        }

        String email = jwtTokenParser.getEmail(e.getClaims());
        TokenService.RefreshResult refreshedToken =
            tokenService.zeroDownRefresh(email, refreshCookie(request));

        if (refreshedToken != null) {
          setAuthentication(refreshedToken.member());
          response.setHeader(
              "Set-Cookie",
              RefreshTokenCookie.createCookie(
                      refreshedToken.tokens().refreshToken(),
                      refreshedToken.tokens().refreshTokenTtl())
                  .toString());
          response.setHeader(AUTH_HEADER, BEARER_PREFIX + refreshedToken.tokens().accessToken());
          response.setHeader("X-Access-Token-Refreshed", "true");
          String exposedHeaders = response.getHeader("Access-Control-Expose-Headers");
          String headersToExpose = AUTH_HEADER + ",X-Access-Token-Refreshed";
          response.setHeader(
              "Access-Control-Expose-Headers",
              exposedHeaders == null ? headersToExpose : exposedHeaders + "," + headersToExpose);
          refreshedAccessToken = true;
        }

        if (!refreshedAccessToken) {
          writeErrorResponse(response, AuthErrorCode.ACCESS_EXPIRED);
          return;
        }
      } catch (Exception e) {
        throw new CatxiException(AuthErrorCode.INVALID_TOKEN);
      }

      if (!refreshedAccessToken) {
        String category = jwtTokenParser.getType(claims);
        if (!"access".equals(category)) {
          throw new CatxiException(AuthErrorCode.INVALID_TOKEN);
        }

        if (tokenBlacklistRepository.isTokenBlacklisted(accessToken)) {
          log.info("블랙리스트 토큰의 요청을 차단했습니다");
          throw new CatxiException(AuthErrorCode.ACCESS_FORBIDDEN);
        }

        String email = jwtTokenParser.getEmail(claims);
        MemberService.MemberAccount member =
            memberService.findAccountByEmailOptional(email).orElse(null);
        if (member == null) {
          throw new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        validateMember(member);
        setAuthentication(member);
      }
    } catch (CatxiException e) {
      writeErrorResponse(response, e.getErrorCode());
      return;
    } catch (RuntimeException e) {
      log.error("인증 정보를 확인하는 중 오류가 발생했습니다.", e);
      writeErrorResponse(response, CommonErrorCode.INTERNAL_SERVER_ERROR);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode)
      throws IOException {
    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(errorCode));
  }

  private void setAuthentication(MemberService.MemberAccount member) {
    String role = member.status() == MemberStatus.PENDING ? "ROLE_PENDING" : member.role();
    MemberUserDetails memberUserDetails = new MemberUserDetails(member.email(), null, role);
    Authentication authToken =
        new UsernamePasswordAuthenticationToken(
            memberUserDetails, null, memberUserDetails.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(authToken);
  }

  private void validateMember(MemberService.MemberAccount member) {
    if (member.status() == MemberStatus.INACTIVE
        || tokenBlacklistRepository.isUserBlacklisted(member.id().toString())) {
      throw new CatxiException(AuthErrorCode.ACCESS_FORBIDDEN);
    }
  }

  private String refreshCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if ("refresh".equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
