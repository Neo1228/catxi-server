package com.catxi.api.config.security;

import com.catxi.auth.config.AuthMode;
import com.catxi.auth.infra.TokenBlacklistRepository;
import com.catxi.auth.jwt.JwtFilter;
import com.catxi.auth.jwt.JwtUtil;
import com.catxi.auth.service.TokenService;
import com.catxi.common.api.ApiResponse;
import com.catxi.common.api.error.CommonErrorCode;
import com.catxi.common.api.error.ErrorCode;
import com.catxi.member.repository.MemberRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  private final JwtUtil jwtUtil;
  private final MemberRepository memberRepository;
  private final TokenBlacklistRepository tokenBlacklistRepository;
  private final CorsConfigurationSource corsConfigurationSource;
  private final ObjectMapper objectMapper;
  private final AuthMode authMode;

  public SecurityConfiguration(
      JwtUtil jwtUtil,
      MemberRepository memberRepository,
      TokenBlacklistRepository tokenBlacklistRepository,
      CorsConfigurationSource corsConfigurationSource,
      ObjectMapper objectMapper,
      Environment environment,
      @Value("${catxi.auth.mode:kakao}") String configuredAuthMode) {
    this.jwtUtil = jwtUtil;
    this.memberRepository = memberRepository;
    this.tokenBlacklistRepository = tokenBlacklistRepository;
    this.corsConfigurationSource = corsConfigurationSource;
    this.objectMapper = objectMapper;
    AuthMode authMode = AuthMode.parse(configuredAuthMode);
    if (authMode == AuthMode.LOCAL
        && !environment.acceptsProfiles(Profiles.of("!prod & (dev | local)"))) {
      throw new IllegalStateException("local 로그인은 dev 또는 local 프로필에서만 사용할 수 있습니다.");
    }
    this.authMode = authMode;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, TokenService tokenService)
      throws Exception {

    // csrf disable
    http.csrf((auth) -> auth.disable());

    // 기본 Form 로그인 방식 disable -> Custom 인증 로직 사용
    http.formLogin((auth) -> auth.disable());

    // http basic 인증 방식 disable (JWT 사용 필수)
    http.httpBasic((auth) -> auth.disable());

    // 세션관리 - JWT 사용으로 무상태성
    http.sessionManagement(
        (session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    // 경로별 인가 작업
    http.authorizeHttpRequests(
        auth -> {
          auth.requestMatchers(
                  "/swagger",
                  "/swagger-ui/**",
                  "/v3/api-docs/**",
                  "/swagger-resources/**",
                  "/webjars/**")
              .permitAll();
          auth.requestMatchers("/connect/**").permitAll();
          if (authMode == AuthMode.KAKAO) {
            auth.requestMatchers("/api/auth/login/kakao", "/api/auth/kakao/callback").permitAll();
          } else {
            auth.requestMatchers("/api/auth/login/kakao", "/api/auth/kakao/callback").denyAll();
          }
          auth.requestMatchers(HttpMethod.POST, "/api/auth/reissue").permitAll();
          if (authMode == AuthMode.LOCAL) {
            auth.requestMatchers(HttpMethod.POST, "/api/members/signUp", "/api/members/login")
                .permitAll();
          } else {
            auth.requestMatchers(HttpMethod.POST, "/api/members/signUp", "/api/members/login")
                .denyAll();
          }
          auth.requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll();
          auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
          auth.anyRequest().authenticated();
        });

    // cors 설정 (origin 명시적 허용)
    http.cors(cors -> cors.configurationSource(corsConfigurationSource));

    http.exceptionHandling(
        exceptions ->
            exceptions
                .authenticationEntryPoint(
                    (request, response, exception) ->
                        writeErrorResponse(response, CommonErrorCode.UNAUTHORIZED))
                .accessDeniedHandler(
                    (request, response, exception) ->
                        writeErrorResponse(response, CommonErrorCode.FORBIDDEN)));

    // JwtFilter
    http.addFilterBefore(
        new JwtFilter(
            jwtUtil,
            tokenService,
            memberRepository,
            tokenBlacklistRepository,
            authMode,
            objectMapper),
        UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode)
      throws IOException {
    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(errorCode));
  }
}
