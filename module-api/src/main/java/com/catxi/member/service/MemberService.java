package com.catxi.member.service;

import com.catxi.common.api.exception.CatxiException;
import com.catxi.common.persistence.DatabaseConstraint;
import com.catxi.member.converter.MemberConverter;
import com.catxi.member.domain.Member;
import com.catxi.member.domain.MemberStatus;
import com.catxi.member.dto.MemberProfileRes;
import com.catxi.member.dto.SignUpDTO;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MemberRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberService {

  private final MemberRepository memberRepository;
  private final BCryptPasswordEncoder bCryptPasswordEncoder;

  public MemberService(
      MemberRepository memberRepository, BCryptPasswordEncoder bCryptPasswordEncoder) {
    this.memberRepository = memberRepository;
    this.bCryptPasswordEncoder = bCryptPasswordEncoder;
  }

  public Long signUp(SignUpDTO dto) {
    validatePasswordLength(dto.getPassword());

    Member member =
        Member.builder()
            .email(dto.getEmail())
            .memberName(dto.getMemberName())
            .nickname(dto.getNickname())
            .studentNo(dto.getStudentNo())
            .password(bCryptPasswordEncoder.encode(dto.getPassword()))
            .matchCount(0)
            .role("ROLE_USER")
            .status(MemberStatus.ACTIVE)
            .build();

    try {
      Member saved = memberRepository.saveAndFlush(member);
      return saved.getId();
    } catch (DataIntegrityViolationException ex) {
      if (DatabaseConstraint.hasName(ex, "uk_member_nickname")) {
        throw new CatxiException(MemberErrorCode.DUPLICATE_NICKNAME);
      }
      if (DatabaseConstraint.hasName(ex, "student_no")) {
        throw new CatxiException(MemberErrorCode.DUPLICATE_STUDENT_NO);
      }
      if (DatabaseConstraint.hasName(ex, "uk_member_email")) {
        throw new CatxiException(MemberErrorCode.DUPLICATE_EMAIL);
      }
      throw ex;
    }
  }

  public static void validatePasswordLength(String password) {
    if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new CatxiException(MemberErrorCode.INVALID_PASSWORD_LENGTH);
    }
  }

  @Transactional
  public void delete(String email) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    member.delete();
  }

  @Transactional(readOnly = true)
  public MemberProfileRes getProfile(String email) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    return MemberConverter.toMemberProfileResponse(member);
  }

  @Transactional(readOnly = true)
  public String findStudentNoByNickname(String nickname) {
    return memberRepository
        .findByNickname(nickname)
        .map(Member::getStudentNo)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
  }
}
