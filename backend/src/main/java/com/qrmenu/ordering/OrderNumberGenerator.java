package com.qrmenu.ordering;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Per-branch, per-day readable order number counter (Section 1.3 risk: "Sipariş
 * numarası çakışması ... DB sequence veya SELECT ... FOR UPDATE ile şube bazlı sayaç
 * güvenceye alınmalı"). Uses EntityManager directly rather than a Spring Data
 * repository/entity - branch_daily_order_sequence is only ever touched through this
 * single atomic upsert, so a full @Entity+@IdClass for a composite (branch_id,
 * order_date) key would be ceremony without benefit. The
 * INSERT ... ON CONFLICT DO UPDATE ... RETURNING statement is atomic at the database
 * level, so concurrent callers for the same branch/day never see or produce a
 * duplicate number, unlike a SELECT-then-INSERT/UPDATE pair.
 */
@Component
class OrderNumberGenerator {

    private final EntityManager entityManager;

    OrderNumberGenerator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    int nextOrderNumber(UUID branchId, LocalDate orderDate) {
        Number result = (Number) entityManager
                .createNativeQuery(
                        "INSERT INTO branch_daily_order_sequence (branch_id, order_date, last_number) "
                                + "VALUES (:branchId, :orderDate, 1) "
                                + "ON CONFLICT (branch_id, order_date) DO UPDATE SET last_number = "
                                + "branch_daily_order_sequence.last_number + 1 "
                                + "RETURNING last_number")
                .setParameter("branchId", branchId)
                .setParameter("orderDate", orderDate)
                .getSingleResult();
        return result.intValue();
    }
}
