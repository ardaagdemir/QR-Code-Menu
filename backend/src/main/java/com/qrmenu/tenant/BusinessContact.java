package com.qrmenu.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Gap-analysis #6 (product-requirements.md Section 12.3): a business can have several
 * owners/report recipients. Purely a data holder for now - no report/notification
 * module consumes dailyReportRecipient/monthlyReportRecipient/whatsappEnabled yet
 * (those land with the reporting/owner-notification roadmap items); this just captures
 * the contact list staff configure ahead of that.
 */
@Entity
@Table(name = "business_contact")
public class BusinessContact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false)
    private String name;

    @Column
    private String phone;

    @Column
    private String email;

    @Column(name = "whatsapp_enabled", nullable = false)
    private boolean whatsappEnabled;

    @Column(name = "daily_report_recipient", nullable = false)
    private boolean dailyReportRecipient;

    @Column(name = "monthly_report_recipient", nullable = false)
    private boolean monthlyReportRecipient;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BusinessContact() {
        // JPA
    }

    public BusinessContact(
            UUID businessId, String name, String phone, String email, boolean whatsappEnabled,
            boolean dailyReportRecipient, boolean monthlyReportRecipient) {
        this.businessId = businessId;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.whatsappEnabled = whatsappEnabled;
        this.dailyReportRecipient = dailyReportRecipient;
        this.monthlyReportRecipient = monthlyReportRecipient;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String name, String phone, String email, boolean whatsappEnabled, boolean dailyReportRecipient,
            boolean monthlyReportRecipient, boolean active) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.whatsappEnabled = whatsappEnabled;
        this.dailyReportRecipient = dailyReportRecipient;
        this.monthlyReportRecipient = monthlyReportRecipient;
        this.active = active;
        this.updatedAt = Instant.now();
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

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public boolean isWhatsappEnabled() {
        return whatsappEnabled;
    }

    public boolean isDailyReportRecipient() {
        return dailyReportRecipient;
    }

    public boolean isMonthlyReportRecipient() {
        return monthlyReportRecipient;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
