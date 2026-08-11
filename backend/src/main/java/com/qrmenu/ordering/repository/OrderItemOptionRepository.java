package com.qrmenu.ordering.repository;

import com.qrmenu.ordering.OrderItemOption;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemOptionRepository extends JpaRepository<OrderItemOption, UUID> {

    List<OrderItemOption> findAllByOrderItemIdIn(List<UUID> orderItemIds);

    void deleteAllByOrderItemId(UUID orderItemId);
}
