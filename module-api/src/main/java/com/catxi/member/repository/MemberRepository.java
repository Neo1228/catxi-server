package com.catxi.member.repository;

import com.catxi.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

  Optional<Member> findByMemberName(String memberName);

  Optional<Member> findByNickname(String nickname);

  // 이메일 기준
  Optional<Member> findByEmail(String email);

  @Modifying
  @Query(
      "UPDATE Member target SET target.matchCount = target.matchCount + 1 WHERE target.id = :memberId")
  int incrementMatchCount(@Param("memberId") Long memberId);

  // 이메일 중복검사
  boolean existsByEmail(String email);

  // 학번 검증용
  boolean existsByStudentNo(String studentNo);

  // 닉네임 중복 조회용
  boolean existsByNickname(String nickname);

  // FCM 토큰으로 회원 조회
  Member findByFcmToken(String fcmToken);

  // 삭제된 회원 조회 불가
}
