package com.qrmenu.expense.repository;

import com.qrmenu.expense.RecurringExpenseTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringExpenseTemplateRepository extends JpaRepository<RecurringExpenseTemplate, UUID> {

    List<RecurringExpenseTemplate> findAllByBusinessIdOrderByStartDateDesc(UUID businessId);

    Optional<RecurringExpenseTemplate> findByIdAndBusinessId(UUID id, UUID businessId);

    List<RecurringExpenseTemplate> findAllByActiveTrue();
}
