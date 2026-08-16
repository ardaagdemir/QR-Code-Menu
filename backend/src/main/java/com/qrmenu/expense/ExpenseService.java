package com.qrmenu.expense;

import com.qrmenu.audit.AuditService;
import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.common.web.StaffPermissionDeniedException;
import com.qrmenu.expense.repository.ExpenseCategoryRepository;
import com.qrmenu.expense.repository.ExpenseRepository;
import com.qrmenu.expense.repository.RecurringExpenseTemplateRepository;
import com.qrmenu.shared.media.LoadedMedia;
import com.qrmenu.shared.media.MediaStoragePort;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public facade for the expense module (gap-analysis #10, product-requirements.md Section
 * 16). Controllers resolve the caller's Permission before calling in here (same convention
 * as every other module); this service additionally enforces per-branch access and the
 * DRAFT/SUBMITTED-editable vs APPROVED/REJECTED-immutable state machine (Section 16.1).
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final RecurringExpenseTemplateRepository templateRepository;
    private final AuditService auditService;
    private final MediaStoragePort mediaStoragePort;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            ExpenseCategoryRepository categoryRepository,
            RecurringExpenseTemplateRepository templateRepository,
            AuditService auditService,
            MediaStoragePort mediaStoragePort) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.templateRepository = templateRepository;
        this.auditService = auditService;
        this.mediaStoragePort = mediaStoragePort;
    }

    @Transactional
    public ExpenseCategory createCategory(StaffContext context, String name) {
        ExpenseCategory category = categoryRepository.save(new ExpenseCategory(context.businessId(), name));
        auditService.record(context.businessId(), context.staffUserId(), "ExpenseCategory", category.getId(), "CREATED", Map.of("name", name));
        return category;
    }

    @Transactional(readOnly = true)
    public List<ExpenseCategory> listCategories(UUID businessId) {
        return categoryRepository.findAllByBusinessIdOrderByNameAsc(businessId);
    }

    @Transactional
    public void deactivateCategory(StaffContext context, UUID categoryId) {
        ExpenseCategory category = categoryRepository
                .findByIdAndBusinessId(categoryId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Expense category not found: " + categoryId));
        category.deactivate();
        categoryRepository.save(category);
        auditService.record(context.businessId(), context.staffUserId(), "ExpenseCategory", categoryId, "DEACTIVATED", Map.of());
    }

    @Transactional
    public Expense createExpense(
            StaffContext context,
            UUID branchId,
            UUID categoryId,
            long amountMinorUnits,
            LocalDate incurredAt,
            String vendor,
            String description,
            String receiptImageUrl) {
        requireCreateAccess(context, branchId);
        requireCategory(context.businessId(), categoryId);
        Expense expense = expenseRepository.save(new Expense(
                context.businessId(),
                branchId,
                categoryId,
                amountMinorUnits,
                incurredAt,
                vendor,
                description,
                receiptImageUrl,
                context.staffUserId(),
                null,
                null));
        auditService.record(context.businessId(), context.staffUserId(), "Expense", expense.getId(), "CREATED", Map.of("amountMinorUnits", amountMinorUnits));
        return expense;
    }

    @Transactional
    public Expense updateDraft(
            StaffContext context,
            UUID expenseId,
            UUID categoryId,
            long amountMinorUnits,
            LocalDate incurredAt,
            String vendor,
            String description,
            String receiptImageUrl) {
        Expense expense = requireEditableExpense(context, expenseId);
        requireCategory(context.businessId(), categoryId);
        expense.applyDraftEdit(categoryId, amountMinorUnits, incurredAt, vendor, description, receiptImageUrl);
        return expenseRepository.save(expense);
    }

    @Transactional
    public Expense submit(StaffContext context, UUID expenseId) {
        Expense expense = requireEditableExpense(context, expenseId);
        expense.submit();
        Expense saved = expenseRepository.save(expense);
        auditService.record(context.businessId(), context.staffUserId(), "Expense", expenseId, "SUBMITTED", Map.of());
        return saved;
    }

    /** Caller must already hold Permission.EXPENSE_APPROVE; expense visibility remains branch-scoped. */
    @Transactional
    public Expense approve(StaffContext context, UUID expenseId) {
        Expense expense = requireSubmittedExpense(context, expenseId);
        expense.approve(context.staffUserId(), Instant.now());
        Expense saved = expenseRepository.save(expense);
        auditService.record(context.businessId(), context.staffUserId(), "Expense", expenseId, "APPROVED", Map.of());
        return saved;
    }

    @Transactional
    public Expense reject(StaffContext context, UUID expenseId) {
        Expense expense = requireSubmittedExpense(context, expenseId);
        expense.reject(context.staffUserId(), Instant.now());
        Expense saved = expenseRepository.save(expense);
        auditService.record(context.businessId(), context.staffUserId(), "Expense", expenseId, "REJECTED", Map.of());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Expense> listExpenses(StaffContext context, UUID branchId, LocalDate from, LocalDate to) {
        if (branchId != null) {
            if (!context.canAccessBranch(branchId)) {
                throw new StaffPermissionDeniedException("Not authorized for branch: " + branchId);
            }
            return expenseRepository.findAllByBusinessIdAndBranchIdAndIncurredAtBetweenOrderByIncurredAtDesc(
                    context.businessId(), branchId, from, to);
        }
        if (context.role() == StaffRole.BUSINESS_ADMIN || context.role() == StaffRole.PLATFORM_ADMIN) {
            return expenseRepository.findAllByBusinessIdAndIncurredAtBetweenOrderByIncurredAtDesc(context.businessId(), from, to);
        }
        if (context.branchIds().isEmpty()) {
            return List.of();
        }
        return expenseRepository.findAllByBranchIdInAndIncurredAtBetweenOrderByIncurredAtDesc(
                List.copyOf(context.branchIds()), from, to);
    }

    /**
     * Backs StaffExpenseController#getReceipt: same tenant/branch scoping as every other
     * per-id lookup in here, but without the editable-state restriction (an APPROVED/
     * REJECTED expense's receipt must still be viewable, just not editable).
     */
    @Transactional(readOnly = true)
    public LoadedMedia loadReceipt(StaffContext context, UUID expenseId) {
        Expense expense = requireViewableExpense(context, expenseId);
        String receiptImageUrl = expense.getReceiptImageUrl();
        if (receiptImageUrl == null) {
            throw new ResourceNotFoundException("Expense has no receipt: " + expenseId);
        }
        return mediaStoragePort
                .resolveKeyFromUrl(receiptImageUrl)
                .flatMap(mediaStoragePort::load)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt file not found: " + expenseId));
    }

    /** Section 17: approved-expense total for the "Yönetimsel Net Sonuç" figure - reporting module's public entry point into this module. */
    @Transactional(readOnly = true)
    public long sumApprovedExpenses(UUID businessId, UUID branchId, LocalDate from, LocalDate to) {
        return expenseRepository.sumApprovedAmount(businessId, branchId, from, to);
    }

    @Transactional
    public RecurringExpenseTemplate createTemplate(
            StaffContext context,
            UUID branchId,
            UUID categoryId,
            long amountMinorUnits,
            String vendor,
            String description,
            int dayOfMonth,
            LocalDate startDate,
            LocalDate endDate) {
        requireCreateAccess(context, branchId);
        requireCategory(context.businessId(), categoryId);
        RecurringExpenseTemplate template = templateRepository.save(new RecurringExpenseTemplate(
                context.businessId(), branchId, categoryId, amountMinorUnits, vendor, description, dayOfMonth, startDate, endDate));
        auditService.record(context.businessId(), context.staffUserId(), "RecurringExpenseTemplate", template.getId(), "CREATED", Map.of());
        return template;
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseTemplate> listTemplates(UUID businessId) {
        return templateRepository.findAllByBusinessIdOrderByStartDateDesc(businessId);
    }

    @Transactional
    public void deactivateTemplate(StaffContext context, UUID templateId) {
        RecurringExpenseTemplate template = templateRepository
                .findByIdAndBusinessId(templateId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurring expense template not found: " + templateId));
        if (!context.canAccessBranch(template.getBranchId())) {
            throw new ResourceNotFoundException("Recurring expense template not found: " + templateId);
        }
        template.deactivate();
        templateRepository.save(template);
        auditService.record(context.businessId(), context.staffUserId(), "RecurringExpenseTemplate", templateId, "DEACTIVATED", Map.of());
    }

    /** Used only by {@link RecurringExpenseScheduler} - no StaffContext exists in a scheduled job. */
    @Transactional
    List<Expense> generateDueDraftsForPeriod(LocalDate today) {
        YearMonth period = YearMonth.from(today);
        return templateRepository.findAllByActiveTrue().stream()
                .filter(template -> isDue(template, today))
                .filter(template -> !expenseRepository.existsBySourceTemplateIdAndGeneratedForPeriod(template.getId(), period.toString()))
                .map(template -> expenseRepository.save(new Expense(
                        template.getBusinessId(),
                        template.getBranchId(),
                        template.getCategoryId(),
                        template.getAmountMinorUnits(),
                        today,
                        template.getVendor(),
                        template.getDescription(),
                        null,
                        null,
                        template.getId(),
                        period)))
                .toList();
    }

    private boolean isDue(RecurringExpenseTemplate template, LocalDate today) {
        if (today.isBefore(template.getStartDate())) {
            return false;
        }
        if (template.getEndDate() != null && today.isAfter(template.getEndDate())) {
            return false;
        }
        int lastDayOfMonth = today.lengthOfMonth();
        int effectiveDay = Math.min(template.getDayOfMonth(), lastDayOfMonth);
        return today.getDayOfMonth() == effectiveDay;
    }

    private void requireCreateAccess(StaffContext context, UUID branchId) {
        if (branchId == null) {
            if (context.role() != StaffRole.BUSINESS_ADMIN && context.role() != StaffRole.PLATFORM_ADMIN) {
                throw new StaffPermissionDeniedException("Only a business admin can create a business-level expense");
            }
            return;
        }
        if (!context.canAccessBranch(branchId)) {
            throw new StaffPermissionDeniedException("Not authorized for branch: " + branchId);
        }
    }

    private void requireCategory(UUID businessId, UUID categoryId) {
        categoryRepository
                .findByIdAndBusinessId(categoryId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense category not found: " + categoryId));
    }

    private Expense requireEditableExpense(StaffContext context, UUID expenseId) {
        Expense expense = requireViewableExpense(context, expenseId);
        if (!expense.isEditable()) {
            throw new IllegalStateException("Expense is not editable in status " + expense.getStatus());
        }
        return expense;
    }

    private Expense requireViewableExpense(StaffContext context, UUID expenseId) {
        Expense expense = expenseRepository
                .findByIdAndBusinessId(expenseId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));
        if (expense.getBranchId() == null) {
            if (context.role() != StaffRole.BUSINESS_ADMIN && context.role() != StaffRole.PLATFORM_ADMIN) {
                throw new StaffPermissionDeniedException("Not authorized for business-level expense");
            }
        } else if (!context.canAccessBranch(expense.getBranchId())) {
            throw new StaffPermissionDeniedException("Not authorized for branch: " + expense.getBranchId());
        }
        return expense;
    }

    private Expense requireSubmittedExpense(StaffContext context, UUID expenseId) {
        Expense expense = requireViewableExpense(context, expenseId);
        if (expense.getStatus() != ExpenseStatus.SUBMITTED) {
            throw new IllegalStateException("Expense is not SUBMITTED (current status: " + expense.getStatus() + ")");
        }
        return expense;
    }
}
