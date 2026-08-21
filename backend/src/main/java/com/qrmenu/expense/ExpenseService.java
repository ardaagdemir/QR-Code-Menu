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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public facade for the expense module (gap-analysis #10, product-requirements.md Section
 * 16). Controllers resolve the caller's Permission before calling in here (same convention
 * as every other module); this service additionally enforces per-branch access and keeps
 * scheduler-generated recurring period snapshots immutable.
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
    public Expense updateManualExpense(
            StaffContext context,
            UUID expenseId,
            UUID categoryId,
            long amountMinorUnits,
            LocalDate incurredAt,
            String vendor,
            String description,
            String receiptImageUrl) {
        Expense expense = requireManualExpense(context, expenseId);
        requireCategory(context.businessId(), categoryId);
        expense.applyManualEdit(categoryId, amountMinorUnits, incurredAt, vendor, description, receiptImageUrl);
        return expenseRepository.save(expense);
    }

    @Transactional(readOnly = true)
    public List<Expense> listExpenses(StaffContext context, UUID branchId, LocalDate from, LocalDate to) {
        if (branchId != null) {
            if (!context.canAccessBranch(branchId)) {
                throw new StaffPermissionDeniedException("Not authorized for branch: " + branchId);
            }
            return expenseRepository.findAllByBusinessIdAndBranchIdAndSourceTemplateIdIsNullAndIncurredAtBetweenOrderByIncurredAtDesc(
                    context.businessId(), branchId, from, to);
        }
        if (context.role() == StaffRole.BUSINESS_ADMIN || context.role() == StaffRole.PLATFORM_ADMIN) {
            return expenseRepository.findAllByBusinessIdAndSourceTemplateIdIsNullAndIncurredAtBetweenOrderByIncurredAtDesc(
                    context.businessId(), from, to);
        }
        if (context.branchIds().isEmpty()) {
            return List.of();
        }
        return expenseRepository.findAllByBranchIdInAndSourceTemplateIdIsNullAndIncurredAtBetweenOrderByIncurredAtDesc(
                List.copyOf(context.branchIds()), from, to);
    }

    /**
     * Backs StaffExpenseController#getReceipt: same tenant/branch scoping as every other
     * per-id lookup in here, but without the manual-edit restriction.
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

    /**
     * Only realized Expense rows are included. Templates themselves are deliberately
     * not queried, so future periods stay out and archived templates retain their past
     * generated expenses in reporting.
     */
    @Transactional(readOnly = true)
    public ExpenseBreakdown expenseBreakdown(UUID businessId, UUID branchId, LocalDate from, LocalDate to) {
        long manualExpenses = expenseRepository.sumManualAmount(businessId, branchId, from, to);
        long recurringExpenses = expenseRepository.sumRecurringAmount(businessId, branchId, from, to);
        return new ExpenseBreakdown(manualExpenses, recurringExpenses);
    }

    public record ExpenseBreakdown(long manualExpensesMinorUnits, long recurringExpensesMinorUnits) {
        public long totalExpensesMinorUnits() {
            return manualExpensesMinorUnits + recurringExpensesMinorUnits;
        }
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
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        YearMonth currentPeriod = YearMonth.from(today);
        RecurringExpenseDuePolicy.dueDate(template, currentPeriod, today)
                .ifPresent(dueDate -> expenseRepository.save(
                        RecurringExpenseDuePolicy.newExpense(template, currentPeriod, dueDate)));
        auditService.record(context.businessId(), context.staffUserId(), "RecurringExpenseTemplate", template.getId(), "CREATED", Map.of());
        return template;
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseTemplate> listTemplates(UUID businessId) {
        return templateRepository.findAllByBusinessIdAndDeletedFalseOrderByStartDateDesc(businessId);
    }

    @Transactional
    public RecurringExpenseTemplate updateTemplate(
            StaffContext context,
            UUID templateId,
            UUID categoryId,
            long amountMinorUnits,
            String vendor,
            String description,
            int dayOfMonth,
            LocalDate startDate,
            LocalDate endDate) {
        RecurringExpenseTemplate template = requireTemplateForMutation(context, templateId);
        requireCategory(context.businessId(), categoryId);
        template.update(categoryId, amountMinorUnits, vendor, description, dayOfMonth, startDate, endDate);
        RecurringExpenseTemplate saved = templateRepository.save(template);
        auditService.record(
                context.businessId(),
                context.staffUserId(),
                "RecurringExpenseTemplate",
                templateId,
                "UPDATED",
                Map.of("amountMinorUnits", amountMinorUnits, "dayOfMonth", dayOfMonth));
        return saved;
    }

    @Transactional
    public void deactivateTemplate(StaffContext context, UUID templateId) {
        RecurringExpenseTemplate template = requireTemplateForMutation(context, templateId);
        template.deactivate();
        templateRepository.save(template);
        auditService.record(context.businessId(), context.staffUserId(), "RecurringExpenseTemplate", templateId, "DEACTIVATED", Map.of());
    }

    @Transactional
    public void activateTemplate(StaffContext context, UUID templateId) {
        RecurringExpenseTemplate template = requireTemplateForMutation(context, templateId);
        template.activate();
        templateRepository.save(template);
        auditService.record(context.businessId(), context.staffUserId(), "RecurringExpenseTemplate", templateId, "ACTIVATED", Map.of());
    }

    /**
     * Logical deletion stops future generation while preserving the source relationship
     * and immutable values of every expense previously generated from this template.
     */
    @Transactional
    public void deleteTemplate(StaffContext context, UUID templateId) {
        RecurringExpenseTemplate template = requireTemplateForMutation(context, templateId);
        template.delete();
        templateRepository.save(template);
        auditService.record(context.businessId(), context.staffUserId(), "RecurringExpenseTemplate", templateId, "DELETED", Map.of());
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

    private Expense requireManualExpense(StaffContext context, UUID expenseId) {
        Expense expense = requireViewableExpense(context, expenseId);
        if (expense.isRecurring()) {
            throw new IllegalStateException("System-generated recurring expenses are immutable");
        }
        return expense;
    }

    private RecurringExpenseTemplate requireTemplateForMutation(StaffContext context, UUID templateId) {
        RecurringExpenseTemplate template = templateRepository
                .findByIdForUpdate(templateId)
                .filter(candidate -> candidate.getBusinessId().equals(context.businessId()) && !candidate.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Recurring expense template not found: " + templateId));
        if (!context.canAccessBranch(template.getBranchId())) {
            throw new ResourceNotFoundException("Recurring expense template not found: " + templateId);
        }
        return template;
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

}
