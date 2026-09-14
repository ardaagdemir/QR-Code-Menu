package com.qrmenu.refund.repository;

import com.qrmenu.refund.Refund;
import com.qrmenu.refund.RefundStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findAllByOrderIdOrderByCreatedAtAsc(UUID orderId);

    /** Gap-analysis #8 reporting: refund toplamı across a set of orders (only COMPLETED refunds count as money out). */
    @Query("select coalesce(sum(r.totalAmountMinorUnits), 0) from Refund r where r.orderId in :orderIds and r.status = :status")
    long sumTotalAmountMinorUnitsByOrderIdInAndStatus(@Param("orderIds") List<UUID> orderIds, @Param("status") RefundStatus status);

    /** Raporlar ekranı "İade Sayısı" KPI'sı: aynı orderId+status kümesinde tamamlanmış iade sayısı. */
    long countByOrderIdInAndStatus(List<UUID> orderIds, RefundStatus status);
}
