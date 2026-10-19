package com.catxi.member.service;

import com.catxi.common.api.exception.CatxiException;
import com.catxi.common.persistence.DatabaseConstraint;
import com.catxi.member.domain.Member;
import com.catxi.member.domain.MemberStatus;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

  public Long signUp(
      String email, String memberName, String password, String studentNo, String nickname) {
    validatePasswordLength(password);

    Member member =
        Member.builder()
            .email(email)
            .memberName(memberName)
            .nickname(nickname)
            .studentNo(studentNo)
            .password(bCryptPasswordEncoder.encode(password))
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
  public MemberProfile getProfile(String email) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    return new MemberProfile(member.getMemberName(), member.getStudentNo(), member.getMatchCount());
  }

  @Transactional(readOnly = true)
  public MemberSummary findSummaryByEmail(String email) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    return new MemberSummary(member.getId(), member.getNickname());
  }

  @Transactional(readOnly = true)
  public Optional<MemberAuthenticationInfo> findAuthenticationInfoByEmail(String email) {
    return memberRepository.findByEmail(email).map(MemberService::toAuthenticationInfo);
  }

  @Transactional(readOnly = true)
  public MemberAccount findAccountByEmail(String email) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    return toAccount(member);
  }

  @Transactional(readOnly = true)
  public Optional<MemberAccount> findAccountByEmailOptional(String email) {
    return memberRepository.findByEmail(email).map(MemberService::toAccount);
  }

  @Transactional(readOnly = true)
  public MemberAccount findAccountById(Long memberId) {
    Member member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    return toAccount(member);
  }

  @Transactional(readOnly = true)
  public Optional<MemberAccount> findAccountByIdOptional(Long memberId) {
    return memberRepository.findById(memberId).map(MemberService::toAccount);
  }

  public MemberChatProfile findChatProfileByEmail(String email) {
    return memberRepository
        .findByEmail(email)
        .map(MemberService::toChatProfile)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
  }

  public MemberChatProfile findChatProfileById(Long memberId) {
    return memberRepository
        .findById(memberId)
        .map(MemberService::toChatProfile)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
  }

  public List<MemberChatProfile> findChatProfilesByIds(Collection<Long> memberIds) {
    if (memberIds.isEmpty()) {
      return List.of();
    }
    return memberRepository.findAllById(memberIds).stream()
        .map(MemberService::toChatProfile)
        .toList();
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public MemberAccount findOrCreateKakaoMember(String email, String nickname) {
    if (nickname == null || nickname.isBlank()) {
      throw new CatxiException(MemberErrorCode.INVALID_KAKAO_PROFILE);
    }

    Optional<Member> existingMember = memberRepository.findByEmail(email);
    if (existingMember.isPresent()) {
      return toAccount(existingMember.get());
    }

    Member newMember =
        Member.builder()
            .email(email)
            .memberName(nickname)
            .password("NO_PASSWORD")
            .matchCount(0)
            .role("ROLE_USER")
            .status(MemberStatus.PENDING)
            .build();
    try {
      return toAccount(memberRepository.saveAndFlush(newMember));
    } catch (DataIntegrityViolationException exception) {
      return memberRepository
          .findByEmail(email)
          .map(MemberService::toAccount)
          .orElseThrow(() -> exception);
    }
  }

  public void completeKakaoSignup(String email, String nickname, String studentNo) {
    Member member =
        memberRepository
            .findByEmailForUpdate(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    if (member.getStatus() != MemberStatus.PENDING) {
      throw new CatxiException(MemberErrorCode.SIGNUP_ALREADY_COMPLETED);
    }

    if (nickname == null || nickname.length() > 9) {
      throw new CatxiException(MemberErrorCode.INVALID_NICKNAME_LENGTH);
    }
    if (memberRepository.existsByNickname(nickname)) {
      throw new CatxiException(MemberErrorCode.DUPLICATE_NICKNAME);
    }
    if (studentNo == null || !studentNo.matches("\\d{9}")) {
      throw new CatxiException(MemberErrorCode.INVALID_STUDENT_NO);
    }
    if (memberRepository.existsByStudentNo(studentNo)) {
      throw new CatxiException(MemberErrorCode.DUPLICATE_STUDENT_NO);
    }

    member.setNickname(nickname);
    member.setStudentNo(studentNo);
    member.setStatus(MemberStatus.ACTIVE);
    try {
      memberRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      if (DatabaseConstraint.hasName(exception, "uk_member_nickname")) {
        throw new CatxiException(MemberErrorCode.DUPLICATE_NICKNAME);
      }
      if (DatabaseConstraint.hasName(exception, "student_no")) {
        throw new CatxiException(MemberErrorCode.DUPLICATE_STUDENT_NO);
      }
      throw exception;
    }
  }

  public boolean isNicknameInUse(String nickname) {
    return memberRepository.existsByNickname(nickname);
  }

  @Transactional(readOnly = true)
  public MemberFcmRegistration findFcmRegistrationByEmail(String email) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    return toFcmRegistration(member);
  }

  @Transactional
  public MemberFcmRegistration updateFcmToken(String email, String token) {
    Member member =
        memberRepository
            .findByEmailForUpdate(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    member.updateFcmToken(token);
    return toFcmRegistration(member);
  }

  @Transactional
  public MemberFcmRegistration updateFcmFid(String email, String fid) {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    memberRepository.clearFcmFidFromOtherMembers(fid, member.getId());
    member =
        memberRepository
            .findByEmailForUpdate(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    member.updateFcmFid(fid);
    memberRepository.flush();
    return toFcmRegistration(member);
  }

  @Transactional
  public MemberFcmRegistration clearFcmRegistration(String email) {
    Member member =
        memberRepository
            .findByEmailForUpdate(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    member.clearFcmRegistration();
    return toFcmRegistration(member);
  }

  private static MemberFcmRegistration toFcmRegistration(Member member) {
    return new MemberFcmRegistration(
        member.getId(),
        member.getFcmToken(),
        member.getFcmTokenUpdatedAt(),
        member.getFcmFid(),
        member.getFcmFidUpdatedAt());
  }

  private static MemberAuthenticationInfo toAuthenticationInfo(Member member) {
    return new MemberAuthenticationInfo(
        member.getId(),
        member.getEmail(),
        member.getPassword(),
        member.getRole(),
        member.getStatus());
  }

  private static MemberAccount toAccount(Member member) {
    return new MemberAccount(
        member.getId(), member.getEmail(), member.getRole(), member.getStatus());
  }

  private static MemberChatProfile toChatProfile(Member member) {
    return new MemberChatProfile(
        member.getId(),
        member.getEmail(),
        member.getMemberName(),
        member.getNickname(),
        member.getMatchCount());
  }

  private String maskStudentNumber(String studentNumber) {
    if (studentNumber.length() <= 3) {
      return "*".repeat(studentNumber.length());
    }
    return "*".repeat(studentNumber.length() - 3)
        + studentNumber.substring(studentNumber.length() - 3);
  }

  public String findStudentNumberByNickname(String nickname) {
    return memberRepository
        .findByNickname(nickname)
        .map(Member::getStudentNo)
        .map(this::maskStudentNumber)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
  }

  public record MemberSummary(Long id, String nickname) {}

  public record MemberProfile(String memberName, String studentNo, int matchCount) {}

  public record MemberAccount(Long id, String email, String role, MemberStatus status) {}

  public record MemberChatProfile(
      Long id, String email, String memberName, String nickname, int matchCount) {}

  public record MemberFcmRegistration(
      Long memberId,
      String token,
      LocalDateTime tokenUpdatedAt,
      String fid,
      LocalDateTime fidUpdatedAt) {}

  public record MemberAuthenticationInfo(
      Long id, String email, String password, String role, MemberStatus status) {}
}
