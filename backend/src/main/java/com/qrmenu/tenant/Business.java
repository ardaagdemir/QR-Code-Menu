package com.qrmenu.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.UUID;

/**
 * Tenant root. Every other tenant-owned table carries a business_id (Section 2) -
 * PLATFORM_ADMIN (business_id = null) is the one exception, and does not exist yet
 * (real StaffUser auth lands in Milestone 8); Business rows are opened via the
 * internal bootstrap API for now.
 *
 * <p>Gap-analysis #6 (Section 12.1): defaultCurrency/defaultTimeZone are business-level
 * settings, not wired into {@link com.qrmenu.shared.Money} math anywhere - the system
 * stays single-currency (Section 5, confirmed decision) and every timestamp is stored
 * in UTC as before. These two fields exist purely as the display/report fallback
 * Section 12.1 asks for (e.g. what a future reporting/receipt screen shows), and as the
 * fallback a {@link Branch} without its own {@code timezone} defers to.
 */
@Entity
@Table(name = "business")
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;

    @Column(name = "default_time_zone", nullable = false, length = 50)
    private String defaultTimeZone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Business() {
        // JPA
    }

    public Business(String name) {
        this(name, "TRY", "Europe/Istanbul");
    }

    public Business(String name, String defaultCurrency, String defaultTimeZone) {
        this.name = name;
        this.active = true;
        this.defaultCurrency = validateCurrency(defaultCurrency);
        this.defaultTimeZone = validateTimeZone(defaultTimeZone);
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void setSettings(String defaultCurrency, String defaultTimeZone) {
        this.defaultCurrency = validateCurrency(defaultCurrency);
        this.defaultTimeZone = validateTimeZone(defaultTimeZone);
        this.updatedAt = Instant.now();
    }

    public void rename(String name) {
        this.name = validateName(name);
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    private static String validateName(String name) {
        String trimmed = name == null ? null : name.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new IllegalArgumentException("Business name must not be blank");
        }
        return trimmed;
    }

    private static String validateCurrency(String currency) {
        try {
            return Currency.getInstance(currency).getCurrencyCode();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid ISO 4217 currency code: " + currency);
        }
    }

    private static String validateTimeZone(String timeZone) {
        try {
            ZoneId.of(timeZone);
            return timeZone;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid IANA time zone id: " + timeZone);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public String getDefaultTimeZone() {
        return defaultTimeZone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
