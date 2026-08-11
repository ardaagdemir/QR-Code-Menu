package com.qrmenu.ownernotification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Gap-analysis #11 (product-requirements.md Section 15): one row per delivery attempt - AUTO
 * (fired once per report+recipient, guarded by {@link
 * com.qrmenu.ownernotification.repository.OwnerNotificationLogRepository#existsByDailyCloseReportIdAndBusinessContactId})
 * or MANUAL (staff "resend", always creates a fresh row regardless of prior attempts). Rows are
 * never updated after insert - an audit trail of what was actually sent/attempted, not a
 * current-state cache.
 */
@Entity
@Table(name = "owner_notification_log")
public class OwnerNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "daily_close_report_id", nullable = false)
    private UUID dailyCloseReportId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "business_contact_id", nullable = false)
    private UUID businessContactId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OwnerNotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OwnerNotificationStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 10)
    private OwnerNotificationTrigger triggeredBy;

    @Column(name = "triggered_by_staff_user_id")
    private UUID triggeredByStaffUserId;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected OwnerNotificationLog() {
        // JPA
    }

    OwnerNotificationLog(
            UUID dailyCloseReportId,
            UUID businessId,
            UUID branchId,
            UUID businessContactId,
            String recipientEmail,
            OwnerNotificationChannel channel,
            OwnerNotificationStatus status,
            String errorMessage,
            OwnerNotificationTrigger triggeredBy,
            UUID triggeredByStaffUserId,
            Instant attemptedAt) {
        this.dailyCloseReportId = dailyCloseReportId;
        this.businessId = businessId;
        this.branchId = branchId;
        this.businessContactId = businessContactId;
        this.recipientEmail = recipientEmail;
        this.channel = channel;
        this.status = status;
        this.errorMessage = errorMessage;
        this.triggeredBy = triggeredBy;
        this.triggeredByStaffUserId = triggeredByStaffUserId;
        this.attemptedAt = attemptedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDailyCloseReportId() {
        return dailyCloseReportId;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getBusinessContactId() {
        return businessContactId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public OwnerNotificationChannel getChannel() {
        return channel;
    }

    public OwnerNotificationStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public OwnerNotificationTrigger getTriggeredBy() {
        return triggeredBy;
    }

    public UUID getTriggeredByStaffUserId() {
        return triggeredByStaffUserId;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
