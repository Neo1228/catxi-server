package com.catxi.notification.fcm.service;

import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.domain.Member;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MemberRepository;
import com.catxi.notification.fcm.dto.FcmTokenUpdateReq;
import com.catxi.notification.fcm.dto.FcmTokenUpdateRes;
import com.catxi.notification.fcm.error.FcmErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FcmTokenService {

  private final MemberRepository memberRepository;

  @Transactional
  public FcmTokenUpdateRes updateFcmToken(String email, FcmTokenUpdateReq request) {
    Member member = requireMember(email);
    try {
      // 토큰 유효성 검증
      if (!StringUtils.hasText(request.token())) {
        throw new CatxiException(FcmErrorCode.INVALID_FCM_TOKEN_FORMAT);
      }

      // 기존 토큰이 있고 같은 토큰인 경우
      if (request.token().equals(member.getFcmToken())) {
        log.info("동일한 FCM 토큰 - Member ID: {}", member.getId());
        return new FcmTokenUpdateRes(member.getId(), member.getFcmTokenUpdatedAt(), true);
      }

      // 새 토큰 업데이트
      member.updateFcmToken(request.token());
      log.info("FCM 토큰 업데이트 - Member ID: {}", member.getId());

      return new FcmTokenUpdateRes(member.getId(), member.getFcmTokenUpdatedAt(), true);

    } catch (CatxiException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "FCM 토큰 처리 실패 - Member ID: {}, ErrorType: {}",
          member.getId(),
          e.getClass().getSimpleName());
      throw new CatxiException(FcmErrorCode.FCM_TOKEN_UPDATE_FAILED);
    }
  }

  @Transactional
  public void deleteFcmToken(String email) {
    Member member = requireMember(email);
    try {
      if (member.getFcmToken() == null) {
        log.info("삭제할 FCM 토큰이 없음 - Member ID: {}", member.getId());
        return; // 이미 토큰이 없는 경우, 바로 종료
      }

      member.updateFcmToken(null);
      log.info("FCM 토큰 삭제 완료 - Member ID: {}", member.getId());

    } catch (Exception e) {
      log.error(
          "FCM 토큰 삭제 실패 - Member ID: {}, ErrorType: {}",
          member.getId(),
          e.getClass().getSimpleName());
      throw new CatxiException(FcmErrorCode.FCM_TOKEN_DELETION_FAILED);
    }
  }

  private Member requireMember(String email) {
    return memberRepository
        .findByEmail(email)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
  }
}
