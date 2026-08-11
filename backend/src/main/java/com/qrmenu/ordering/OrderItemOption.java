package com.qrmenu.ordering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Same snapshot reasoning as OrderItem - optionId is kept only for traceability. */
@Entity
@Table(name = "order_item_option")
public class OrderItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "option_id")
    private UUID optionId;

    @Column(name = "option_name_snapshot", nullable = false)
    private String optionNameSnapshot;

    @Column(name = "price_delta_minor_units", nullable = false)
    private long priceDeltaMinorUnits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderItemOption() {
        // JPA
    }

    public OrderItemOption(UUID orderItemId, UUID optionId, String optionNameSnapshot, long priceDeltaMinorUnits) {
        this.orderItemId = orderItemId;
        this.optionId = optionId;
        this.optionNameSnapshot = optionNameSnapshot;
        this.priceDeltaMinorUnits = priceDeltaMinorUnits;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderItemId() {
        return orderItemId;
    }

    public UUID getOptionId() {
        return optionId;
    }

    public String getOptionNameSnapshot() {
        return optionNameSnapshot;
    }

    public long getPriceDeltaMinorUnits() {
        return priceDeltaMinorUnits;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
