package com.qrmenu.expense.repository;

import com.qrmenu.expense.Expense;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Optional<Expense> findByIdAndBusinessId(UUID id, UUID businessId);

    List<Expense> findAllByBusinessIdAndIncurredAtBetweenOrderByIncurredAtDesc(
            UUID businessId, LocalDate from, LocalDate to);

    List<Expense> findAllByBusinessIdAndBranchIdAndIncurredAtBetweenOrderByIncurredAtDesc(
            UUID businessId, UUID branchId, LocalDate from, LocalDate to);

    List<Expense> findAllByBranchIdInAndIncurredAtBetweenOrderByIncurredAtDesc(
            List<UUID> branchIds, LocalDate from, LocalDate to);

    boolean existsBySourceTemplateIdAndGeneratedForPeriod(UUID sourceTemplateId, String generatedForPeriod);

    @Query(
            "SELECT COALESCE(SUM(e.amountMinorUnits), 0) FROM Expense e "
                    + "WHERE e.businessId = :businessId "
                    + "AND (:branchId IS NULL OR e.branchId = :branchId) "
                    + "AND e.status = com.qrmenu.expense.ExpenseStatus.APPROVED "
                    + "AND e.incurredAt BETWEEN :from AND :to")
    long sumApprovedAmount(
            @Param("businessId") UUID businessId,
            @Param("branchId") UUID branchId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
