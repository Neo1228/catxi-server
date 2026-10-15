package com.catxi.report.repository;

import com.catxi.report.domain.Report;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

  @Query(
      "SELECT r FROM Report r WHERE r.roomId = :roomId AND r.reporter.id = :reporterId AND r.reportedMember.id = :reportedMemberId")
  Optional<Report> findDuplicateReport(
      @Param("roomId") Long roomId,
      @Param("reporterId") Long reporterId,
      @Param("reportedMemberId") Long reportedMemberId);
}
