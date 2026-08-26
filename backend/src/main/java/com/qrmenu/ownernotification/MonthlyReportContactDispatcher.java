package com.qrmenu.ownernotification;

import com.qrmenu.ownernotification.repository.OwnerNotificationLogRepository;
import com.qrmenu.tenant.BusinessContact;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A separate bean (not a method called via {@code this.} on {@link OwnerNotificationService}) so
 * {@code @Transactional} actually goes through the Spring proxy - same self-invocation pitfall
 * {@code MockPaymentSimulationDispatcher} documents for {@code @Async}.
 *
 * <p>Two layers of concurrency-safety per (branchId, periodMonth, contact), matching how
 * V38/V39 pair an app-layer check with a DB-level backstop: (1) {@link #tryAdvisoryLock} - a
 * Postgres transaction-scoped advisory lock so two concurrent scheduler instances never both
 * send the same contact's email (the lock auto-releases at commit/rollback, no schema needed -
 * same native-query-via-EntityManager idiom as {@code OrderNumberGenerator}); (2) the V40
 * partial unique index on (branch_id, report_period, business_contact_id) WHERE
 * report_type='MONTHLY' AND triggered_by='AUTO' AND status='SENT', which makes it physically
 * impossible for two SENT rows to persist even if the lock were somehow bypassed - caught here
 * as {@link DataIntegrityViolationException} and treated as "already sent elsewhere".
 *
 * <p>Retry/backoff: unlike the daily flow (where AUTO idempotency is "any prior attempt, SENT or
 * FAILED, blocks retries" - acceptable there because FINAL only fires once per day), a monthly
 * FAILED attempt must be retryable without spamming SMTP every 5 minutes for the entire day-1
 * window the scheduler polls. So the skip condition here only treats SENT as terminal; a FAILED
 * attempt is retried only after {@link #RETRY_BACKOFF} has passed since the last attempt.
 */
@Component
class MonthlyReportContactDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportContactDispatcher.class);
    private static final Duration RETRY_BACKOFF = Duration.ofHours(1);

    private final OwnerNotificationLogRepository repository;
    private final OwnerNotificationPort emailPort;
    private final EntityManager entityManager;

    MonthlyReportContactDispatcher(
            OwnerNotificationLogRepository repository, OwnerNotificationPort emailPort, EntityManager entityManager) {
        this.repository = repository;
        this.emailPort = emailPort;
        this.entityManager = entityManager;
    }

    @Transactional
    void attemptForContact(
            UUID businessId, UUID branchId, YearMonth periodMonth, BusinessContact contact, String subject, String body) {
        LocalDate period = periodMonth.atDay(1);
        if (!tryAdvisoryLock(branchId, period, contact.getId())) {
            // Another instance is handling (or just finished handling) this exact contact right
            // now; this poll cycle backs off and lets that instance own the outcome.
            return;
        }

        Optional<OwnerNotificationLog> lastAttempt = repository
                .findTopByReportTypeAndBranchIdAndReportPeriodAndBusinessContactIdOrderByAttemptedAtDesc(
                        OwnerNotificationReportType.MONTHLY, branchId, period, contact.getId());
        if (lastAttempt.isPresent()) {
            OwnerNotificationLog last = lastAttempt.get();
            if (last.getStatus() == OwnerNotificationStatus.SENT) {
                return;
            }
            if (last.getAttemptedAt().isAfter(Instant.now().minus(RETRY_BACKOFF))) {
                return;
            }
        }

        OwnerNotificationStatus status;
        String errorMessage = null;
        try {
            emailPort.send(new OwnerNotificationMessage(contact.getEmail(), subject, body));
            status = OwnerNotificationStatus.SENT;
        } catch (OwnerNotificationDeliveryException e) {
            log.warn(
                    "Aylık rapor bildirimi gönderilemedi (branchId={}, period={}, contactId={}): {}",
                    branchId,
                    period,
                    contact.getId(),
                    e.getMessage());
            status = OwnerNotificationStatus.FAILED;
            errorMessage = e.getMessage();
        }

        OwnerNotificationLog logRow = new OwnerNotificationLog(
                null,
                businessId,
                branchId,
                contact.getId(),
                contact.getEmail(),
                OwnerNotificationChannel.EMAIL,
                status,
                errorMessage,
                OwnerNotificationTrigger.AUTO,
                null,
                Instant.now(),
                OwnerNotificationReportType.MONTHLY,
                period);
        try {
            repository.save(logRow);
        } catch (DataIntegrityViolationException e) {
            log.warn(
                    "Aylık rapor için eşzamanlı bir gönderim zaten kaydedilmiş (branchId={}, period={}, contactId={})",
                    branchId,
                    period,
                    contact.getId());
        }
    }

    private boolean tryAdvisoryLock(UUID branchId, LocalDate period, UUID contactId) {
        String key = "monthly-report:" + branchId + ":" + period + ":" + contactId;
        Object result = entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", key)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }
}
