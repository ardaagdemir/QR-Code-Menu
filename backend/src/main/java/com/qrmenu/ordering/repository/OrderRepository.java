package com.qrmenu.ordering.repository;

import com.qrmenu.ordering.CustomerOrder;
import com.qrmenu.ordering.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

    Optional<CustomerOrder> findByTableVisitIdAndStatus(UUID tableVisitId, OrderStatus status);

    List<CustomerOrder> findAllByStatusAndLastActivityAtBefore(OrderStatus status, Instant cutoff);

    /** DRAFT (first attempt) or PAYMENT_FAILED (retry) - see CustomerOrder.markAwaitingPayment. */
    Optional<CustomerOrder> findFirstByTableVisitIdAndStatusIn(UUID tableVisitId, List<OrderStatus> statuses);

    /** KDS queue listing (Milestone 6) - orderNumber ascending puts the oldest paid order first. */
    List<CustomerOrder> findAllByBranchIdAndStatusOrderByOrderNumberAsc(UUID branchId, OrderStatus status);

    Optional<CustomerOrder> findByTrackingTokenHash(String trackingTokenHash);

    /** Staff-facing order lookup by its readable order number (Milestone 7 refund flow). */
    Optional<CustomerOrder> findByBranchIdAndOrderNumber(UUID branchId, Integer orderNumber);
}
