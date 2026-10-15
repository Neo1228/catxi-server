package com.catxi.auth.service;

import com.catxi.auth.security.CustomUserDetails;
import com.catxi.member.domain.Member;
import com.catxi.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final MemberRepository memberRepository;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

    // Email 기준 조회
    Member member =
        memberRepository
            .findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException("이메일 또는 비밀번호가 올바르지 않습니다."));

    log.info("로그인 시도: memberId={}, status={}", member.getId(), member.getStatus());

    // 회원 상태 조회
    switch (member.getStatus()) {
      case INACTIVE:
        throw new DisabledException("탈퇴한 회원입니다.");
      case PENDING:
        // 인증 대기 상태의 회원도 로그인은 허용합니다.
        return new CustomUserDetails(member);
      case ACTIVE:
        return new CustomUserDetails(member);
      default:
        throw new DisabledException("유효하지 않은 회원 상태입니다.");
    }
  }
}
