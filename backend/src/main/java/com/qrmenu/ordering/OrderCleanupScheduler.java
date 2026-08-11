package com.qrmenu.ordering;

import com.qrmenu.ordering.repository.OrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Terk edilmiş DRAFT sepetlerin otomatik temizlenmesi" (Section 7): a DRAFT order with
 * no activity (item added/removed) for DRAFT_TTL is auto-cancelled. 2 hours is
 * RECOMMENDED, not a fixed user decision (Section 1.2, item 11).
 */
@Component
class OrderCleanupScheduler {

    static final Duration DRAFT_TTL = Duration.ofHours(2);

    private final OrderRepository orderRepository;

    OrderCleanupScheduler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    @Transactional
    void cancelStaleDraftOrders() {
        Instant cutoff = Instant.now().minus(DRAFT_TTL);
        List<CustomerOrder> staleDrafts = orderRepository.findAllByStatusAndLastActivityAtBefore(OrderStatus.DRAFT, cutoff);
        staleDrafts.forEach(CustomerOrder::cancel);
        orderRepository.saveAll(staleDrafts);
    }
}
