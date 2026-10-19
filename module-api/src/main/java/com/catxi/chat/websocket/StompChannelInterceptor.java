package com.catxi.chat.websocket;

import com.catxi.auth.error.AuthErrorCode;
import com.catxi.auth.jwt.JwtTokenParser;
import com.catxi.auth.service.BlacklistService;
import com.catxi.chat.service.ChatRoomService;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.domain.MemberStatus;
import com.catxi.member.service.MemberService;
import io.jsonwebtoken.Claims;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompChannelInterceptor implements ChannelInterceptor {

  private final JwtTokenParser jwtTokenParser;
  private final ChatRoomService chatRoomService;
  private final MemberService memberService;
  private final BlacklistService blacklistService;
  private final RoomSubscriptionRegistry roomSubscriptions;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    if (StompCommand.UNSUBSCRIBE == accessor.getCommand()) {
      roomSubscriptions.remove(accessor.getSessionId(), accessor.getSubscriptionId());
    }

    if (StompCommand.CONNECT == accessor.getCommand()) {
      String token = extractToken(accessor);
      Claims claims;
      try {
        claims = jwtTokenParser.parseJwt(token);
      } catch (Exception e) {
        throw new CatxiException(AuthErrorCode.ACCESS_EXPIRED);
      }

      if (!"access".equals(jwtTokenParser.getType(claims))) {
        throw new CatxiException(AuthErrorCode.INVALID_TOKEN);
      }
      if (blacklistService.isTokenBlacklisted(token)) {
        throw new CatxiException(AuthErrorCode.ACCESS_FORBIDDEN);
      }

      String email = jwtTokenParser.getEmail(claims);
      MemberService.MemberAccount member = memberService.findAccountByEmail(email);
      validateMember(member);
      accessor.setUser(
          new UsernamePasswordAuthenticationToken(email, token, Collections.emptyList()));
      log.info("CONNECT authentication established");
    }

    if (StompCommand.SEND == accessor.getCommand()) {
      if (!isSupportedSendDestination(accessor.getDestination())) {
        throw new AuthenticationServiceException("지원하지 않는 SEND destination");
      }
      validateSession(accessor);
    }

    if (StompCommand.SUBSCRIBE == accessor.getCommand()) {
      MemberService.MemberAccount member = validateSession(accessor);
      String destination = accessor.getDestination();
      if (destination == null) {
        throw new AuthenticationServiceException("destination 누락");
      }
      if ("/user/queue/kick".equals(destination) || "/user/queue/chat-ack".equals(destination)) {
        return message;
      }

      Long roomId = extractSubscribedRoomId(destination);
      if (roomId == null) {
        throw new AuthenticationServiceException("지원하지 않는 SUBSCRIBE destination");
      }

      String email = resolveEmail(accessor);
      if (!chatRoomService.isRoomParticipant(email, roomId)) {
        log.warn("SUBSCRIBE denied: roomId={}", roomId);
        throw new AuthenticationServiceException("해당 room에 권한이 없습니다");
      }

      RoomSubscriptionRegistry.Revisions revisions;
      try {
        revisions = roomSubscriptions.captureRevisions(roomId, member.id());
      } catch (RuntimeException exception) {
        throw new AuthenticationServiceException("채팅방 권한을 확인할 수 없습니다", exception);
      }
      // 권한 확인과 세대 캡처 사이에 강퇴가 커밋됐으면 구독을 거부합니다.
      if (!chatRoomService.isRoomParticipant(email, roomId)) {
        log.warn("SUBSCRIBE denied after access revision capture: roomId={}", roomId);
        throw new AuthenticationServiceException("해당 room에 권한이 없습니다");
      }
      roomSubscriptions.register(
          accessor.getSessionId(), accessor.getSubscriptionId(), roomId, member.id(), revisions);
    }

    return message;
  }

  private String resolveEmail(StompHeaderAccessor accessor) {
    if (accessor.getUser() == null
        || accessor.getUser().getName() == null
        || accessor.getUser().getName().isBlank()) {
      throw new AuthenticationServiceException("인증 정보가 없습니다");
    }
    return accessor.getUser().getName();
  }

  private boolean isSupportedSendDestination(String destination) {
    if (destination == null) {
      return false;
    }
    String[] segments = destination.split("/", -1);
    return (segments.length == 3
            && "".equals(segments[0])
            && "publish".equals(segments[1])
            && parseRoomId(segments[2]) != null)
        || (segments.length == 4
            && "".equals(segments[0])
            && "publish".equals(segments[1])
            && "map".equals(segments[2])
            && parseRoomId(segments[3]) != null);
  }

  static Long extractSubscribedRoomId(String destination) {
    String[] segments = destination.split("/", -1);
    if (segments.length == 3 && "".equals(segments[0]) && "topic".equals(segments[1])) {
      return parseRoomId(segments[2]);
    }
    if (segments.length == 4
        && "".equals(segments[0])
        && "topic".equals(segments[1])
        && ("chat".equals(segments[2])
            || "map".equals(segments[2])
            || "ready".equals(segments[2]))) {
      return parseRoomId(segments[3]);
    }
    if (segments.length == 5 && "".equals(segments[0]) && "topic".equals(segments[1])) {
      if ("ready".equals(segments[2]) && "result".equals(segments[4])) {
        return parseRoomId(segments[3]);
      }
      if ("room".equals(segments[2])
          && ("participants".equals(segments[4]) || "deleted".equals(segments[4]))) {
        return parseRoomId(segments[3]);
      }
    }
    return null;
  }

  private static Long parseRoomId(String value) {
    if (value == null || value.isBlank() || !value.chars().allMatch(Character::isDigit)) {
      return null;
    }
    try {
      long roomId = Long.parseLong(value);
      return roomId > 0 ? roomId : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private void validateMember(MemberService.MemberAccount member) {
    if (member.status() == MemberStatus.PENDING) {
      throw new CatxiException(AuthErrorCode.SIGNUP_REQUIRED);
    }
    if (member.status() == MemberStatus.INACTIVE
        || blacklistService.isUserBlacklisted(member.id())) {
      throw new CatxiException(AuthErrorCode.ACCESS_FORBIDDEN);
    }
  }

  private MemberService.MemberAccount validateSession(StompHeaderAccessor accessor) {
    String email = resolveEmail(accessor);
    if (!(accessor.getUser() instanceof Authentication authentication)) {
      throw new CatxiException(AuthErrorCode.ACCESS_FORBIDDEN);
    }
    Object credentials = authentication.getCredentials();
    if (!(credentials instanceof String token) || blacklistService.isTokenBlacklisted(token)) {
      throw new CatxiException(AuthErrorCode.ACCESS_FORBIDDEN);
    }
    MemberService.MemberAccount member = memberService.findAccountByEmail(email);
    validateMember(member);
    return member;
  }

  private String extractToken(StompHeaderAccessor accessor) {
    String bearerToken = accessor.getFirstNativeHeader("Authorization");
    if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
      throw new AuthenticationServiceException("Authorization 헤더가 잘못되었습니다");
    }
    String token = bearerToken.substring(7).trim();
    if (token.isBlank()) {
      throw new AuthenticationServiceException("Authorization 헤더가 잘못되었습니다");
    }
    return token;
  }
}
