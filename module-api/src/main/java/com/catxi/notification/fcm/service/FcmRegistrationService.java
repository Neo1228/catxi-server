package com.catxi.notification.fcm.service;

import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.service.MemberService;
import com.catxi.notification.fcm.error.FcmErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FcmRegistrationService {

  private final MemberService memberService;

  @Transactional
  public MemberService.MemberFcmRegistration updateFcmToken(String email, String token) {
    MemberService.MemberFcmRegistration member = memberService.findFcmRegistrationByEmail(email);
    try {
      // 토큰 유효성 검증
      if (!StringUtils.hasText(token)) {
        throw new CatxiException(FcmErrorCode.INVALID_FCM_TOKEN_FORMAT);
      }

      // 기존 토큰이 있고 같은 토큰인 경우
      if (token.equals(member.token())) {
        member = memberService.updateFcmToken(email, token);
        log.info("FCM 토큰 등록 시각 갱신 - Member ID: {}", member.memberId());
        return member;
      }

      if (member.fid() != null) {
        log.info("기존 FCM 설치 ID를 유지합니다 - Member ID: {}", member.memberId());
        return member;
      }

      // 새 토큰 업데이트
      member = memberService.updateFcmToken(email, token);
      log.info("FCM 토큰 업데이트 - Member ID: {}", member.memberId());

      return member;

    } catch (CatxiException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "FCM 토큰 처리 실패 - Member ID: {}, ErrorType: {}",
          member.memberId(),
          e.getClass().getSimpleName());
      throw new CatxiException(FcmErrorCode.FCM_REGISTRATION_UPDATE_FAILED);
    }
  }

  @Transactional
  public MemberService.MemberFcmRegistration updateFcmFid(String email, String fid) {
    MemberService.MemberFcmRegistration member = memberService.findFcmRegistrationByEmail(email);
    try {
      if (!StringUtils.hasText(fid) || fid.length() > 255 || !fid.equals(fid.trim())) {
        throw new CatxiException(FcmErrorCode.INVALID_FCM_FID_FORMAT);
      }

      member = memberService.updateFcmFid(email, fid);
      log.info("FCM 설치 ID 등록 시각 갱신 - Member ID: {}", member.memberId());
      return member;
    } catch (CatxiException e) {
      throw e;
    } catch (DataIntegrityViolationException e) {
      throw new CatxiException(FcmErrorCode.FCM_FID_ALREADY_REGISTERED);
    } catch (Exception e) {
      log.error(
          "FCM 설치 ID 업데이트 실패 - Member ID: {}, ErrorType: {}",
          member.memberId(),
          e.getClass().getSimpleName());
      throw new CatxiException(FcmErrorCode.FCM_REGISTRATION_UPDATE_FAILED);
    }
  }

  @Transactional
  public void deleteFcmRegistration(String email) {
    MemberService.MemberFcmRegistration member = memberService.findFcmRegistrationByEmail(email);
    try {
      if (member.token() == null && member.fid() == null) {
        log.info("삭제할 FCM 등록 정보가 없음 - Member ID: {}", member.memberId());
        return; // 이미 토큰이 없는 경우, 바로 종료
      }

      memberService.clearFcmRegistration(email);
      log.info("FCM 토큰 삭제 완료 - Member ID: {}", member.memberId());

    } catch (Exception e) {
      log.error(
          "FCM 토큰 삭제 실패 - Member ID: {}, ErrorType: {}",
          member.memberId(),
          e.getClass().getSimpleName());
      throw new CatxiException(FcmErrorCode.FCM_REGISTRATION_DELETE_FAILED);
    }
  }
}
