package com.qrmenu.refund.repository;

import com.qrmenu.refund.RefundItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundItemRepository extends JpaRepository<RefundItem, UUID> {

    List<RefundItem> findAllByRefundIdIn(List<UUID> refundIds);

    /** Only successful refunds consume an order item's refundable quantity. */
    @Query("""
            select ri.orderItemId, sum(ri.refundedQuantity)
            from RefundItem ri, Refund r
            where ri.refundId = r.id
              and r.orderId = :orderId
              and r.status = com.qrmenu.refund.RefundStatus.COMPLETED
            group by ri.orderItemId
            """)
    List<Object[]> sumCompletedRefundedQuantityByOrderItem(@Param("orderId") UUID orderId);
}
