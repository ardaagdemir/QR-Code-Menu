package com.qrmenu.refund.repository;

import com.qrmenu.refund.RefundItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundItemRepository extends JpaRepository<RefundItem, UUID> {

    List<RefundItem> findAllByRefundIdIn(List<UUID> refundIds);
}
