package com.qrmenu.expense.repository;

import com.qrmenu.expense.ExpenseCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {

    List<ExpenseCategory> findAllByBusinessIdOrderByNameAsc(UUID businessId);

    Optional<ExpenseCategory> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<ExpenseCategory> findByBusinessIdAndNameIgnoreCase(UUID businessId, String name);
}
