package com.qrmenu.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Section 16.1: "Gider kategorileri manuel yönetilebilir" - business-scoped, no branch dimension. */
@Entity
@Table(name = "expense_category")
public class ExpenseCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    protected ExpenseCategory() {
        // JPA
    }

    ExpenseCategory(UUID businessId, String name) {
        this.businessId = businessId;
        this.name = name;
    }

    void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
