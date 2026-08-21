package com.qrmenu.expense.repository;

import com.qrmenu.expense.RecurringExpenseTemplate;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringExpenseTemplateRepository extends JpaRepository<RecurringExpenseTemplate, UUID> {

    List<RecurringExpenseTemplate> findAllByBusinessIdAndDeletedFalseOrderByStartDateDesc(UUID businessId);

    Optional<RecurringExpenseTemplate> findByIdAndBusinessIdAndDeletedFalse(UUID id, UUID businessId);

    List<RecurringExpenseTemplate> findAllByActiveTrueAndDeletedFalse();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT template FROM RecurringExpenseTemplate template WHERE template.id = :id")
    Optional<RecurringExpenseTemplate> findByIdForUpdate(@Param("id") UUID id);
}
