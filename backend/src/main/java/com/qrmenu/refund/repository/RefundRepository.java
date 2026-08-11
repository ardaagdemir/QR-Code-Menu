package com.qrmenu.refund.repository;

import com.qrmenu.refund.Refund;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findAllByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
