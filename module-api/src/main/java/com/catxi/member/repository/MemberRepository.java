package com.catxi.member.repository;

import com.catxi.member.domain.Member;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT member FROM Member member WHERE member.email = :email")
  Optional<Member> findByEmailForUpdate(@Param("email") String email);

  @Modifying
  @Query(
      "UPDATE Member member SET member.fcmFid = NULL, member.fcmFidUpdatedAt = NULL "
          + "WHERE member.fcmFid = :fid AND member.id <> :memberId")
  int clearFcmFidFromOtherMembers(@Param("fid") String fid, @Param("memberId") Long memberId);

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

  // 삭제된 회원 조회 불가
}
