package com.qrmenu.dailyclose.repository;

import com.qrmenu.dailyclose.DailyBranchCloseReport;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyCloseReportRepository extends JpaRepository<DailyBranchCloseReport, UUID> {

    Optional<DailyBranchCloseReport> findByBranchIdAndBusinessDate(UUID branchId, LocalDate businessDate);

    List<DailyBranchCloseReport> findAllByBranchIdAndBusinessDateBetweenOrderByBusinessDateAsc(
            UUID branchId, LocalDate from, LocalDate to);

    List<DailyBranchCloseReport> findAllByBusinessIdAndBusinessDateBetweenOrderByBranchIdAscBusinessDateAsc(
            UUID businessId, LocalDate from, LocalDate to);
}
