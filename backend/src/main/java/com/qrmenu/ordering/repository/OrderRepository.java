package com.qrmenu.ordering.repository;

import com.qrmenu.ordering.CustomerOrder;
import com.qrmenu.ordering.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

    Optional<CustomerOrder> findByTableVisitIdAndStatus(UUID tableVisitId, OrderStatus status);

    List<CustomerOrder> findAllByStatusAndLastActivityAtBefore(OrderStatus status, Instant cutoff);

    /** DRAFT (first attempt) or PAYMENT_FAILED (retry) - see CustomerOrder.markAwaitingPayment. */
    Optional<CustomerOrder> findFirstByTableVisitIdAndStatusIn(UUID tableVisitId, List<OrderStatus> statuses);

    /** KDS queue listing (Milestone 6) - orderNumber ascending puts the oldest paid order first. */
    List<CustomerOrder> findAllByBranchIdAndStatusOrderByOrderNumberAsc(UUID branchId, OrderStatus status);

    Optional<CustomerOrder> findByTrackingTokenHash(String trackingTokenHash);

    /** Serializes refund attempts for one order so item-level remaining quantities cannot race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Staff-facing order lookup by its readable order number (Milestone 7 refund flow).
     * orderNumber resets to 1 per branch per day (OrderNumberGenerator), so it is NOT
     * globally unique for a branch over time - more than one order can share the same
     * number once a branch has operated for more than a day. Returns every match,
     * newest first, so the caller can resolve ambiguity (see OrderingService.
     * getOrderByNumber) instead of a single-result derived query throwing
     * IncorrectResultSizeDataAccessException on the second colliding row.
     */
    List<CustomerOrder> findAllByBranchIdAndOrderNumberOrderByCreatedAtDesc(UUID branchId, Integer orderNumber);

    /** Siparişler (order history) screen: completed/rejected orders for a branch within a date range. */
    List<CustomerOrder> findAllByBranchIdAndStatusInAndCreatedAtBetweenOrderByLastActivityAtDesc(
            UUID branchId, List<OrderStatus> statuses, Instant from, Instant to);

    /** Gap-analysis #7 chain comparison: real orders only, abandoned DRAFT/CANCELLED carts excluded. */
    long countByBranchIdAndCreatedAtAfterAndStatusNotIn(UUID branchId, Instant since, List<OrderStatus> excludedStatuses);

    /** Gap-analysis #8 reporting: paid orders (successful payment) for a branch within a selectable date range. */
    List<CustomerOrder> findAllByBranchIdAndCreatedAtBetweenAndStatusIn(
            UUID branchId, Instant from, Instant to, List<OrderStatus> statuses);

    long countByBranchIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            UUID branchId, OrderStatus status, Instant from, Instant to);

    List<CustomerOrder> findAllByBranchIdAndReadyAtGreaterThanEqualAndReadyAtLessThanAndPreparationStartedAtIsNotNull(
            UUID branchId, Instant from, Instant to);
}
