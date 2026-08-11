package com.qrmenu.ordering.repository;

import com.qrmenu.ordering.OrderItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findAllByOrderId(UUID orderId);

    List<OrderItem> findAllByOrderIdIn(List<UUID> orderIds);

    Optional<OrderItem> findByIdAndOrderId(UUID id, UUID orderId);
}
