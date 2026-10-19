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
    SELECT m FROM MatchHistory m
    WHERE m.user.email = :email
    ORDER BY m.createdAt DESC, m.id DESC
""")
  Slice<MatchHistory> findByUserEmail(@Param("email") String email, Pageable pageable);
}
