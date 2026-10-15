package com.catxi.member.repository;

import com.catxi.member.domain.MatchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long> {

  boolean existsByRoomIdAndUser_Id(Long roomId, Long userId);

  @Query(
"""
    SELECT DISTINCT m FROM MatchHistory m
    LEFT JOIN m.participants participant
    WHERE m.user.email = :email
       OR participant = :nickname
    ORDER BY m.createdAt DESC, m.id DESC
""")
  Slice<MatchHistory> findHistoriesByUserOrParticipant(
      @Param("email") String email, @Param("nickname") String nickname, Pageable pageable);
}
