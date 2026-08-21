package com.qrmenu.expense.web;

import com.qrmenu.expense.Expense;
import com.qrmenu.expense.ExpenseCategory;
import com.qrmenu.expense.ExpenseService;
import com.qrmenu.expense.RecurringExpenseTemplate;
import com.qrmenu.expense.web.dto.CreateExpenseCategoryRequest;
import com.qrmenu.expense.web.dto.CreateExpenseRequest;
import com.qrmenu.expense.web.dto.CreateRecurringExpenseTemplateRequest;
import com.qrmenu.expense.web.dto.ExpenseCategoryResponse;
import com.qrmenu.expense.web.dto.ExpenseResponse;
import com.qrmenu.expense.web.dto.RecurringExpenseTemplateResponse;
import com.qrmenu.expense.web.dto.UpdateExpenseRequest;
import com.qrmenu.expense.web.dto.UpdateRecurringExpenseTemplateRequest;
import com.qrmenu.shared.media.LoadedMedia;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.common.web.StaffPermissionDeniedException;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Gap-analysis #10 (product-requirements.md Section 16): gider kategorileri, giderler ve tekrarlayan gider şablonları. */
@RestController
public class StaffExpenseController {

    private final ExpenseService expenseService;
    private final StaffAuthService staffAuthService;

    public StaffExpenseController(ExpenseService expenseService, StaffAuthService staffAuthService) {
        this.expenseService = expenseService;
        this.staffAuthService = staffAuthService;
    }

    @GetMapping("/api/staff/expense-categories")
    public List<ExpenseCategoryResponse> listCategories(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = requireView(sessionCookie);
        return expenseService.listCategories(context.businessId()).stream()
                .map(StaffExpenseController::toResponse)
                .toList();
    }

    @PostMapping("/api/staff/expense-categories")
    public ResponseEntity<ExpenseCategoryResponse> createCategory(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody CreateExpenseCategoryRequest request) {
        StaffContext context = requireManage(sessionCookie);
        ExpenseCategory category = expenseService.createCategory(context, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(category));
    }

    @PostMapping("/api/staff/expense-categories/{categoryId}/deactivate")
    public void deactivateCategory(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID categoryId) {
        StaffContext context = requireManage(sessionCookie);
        expenseService.deactivateCategory(context, categoryId);
    }

    @GetMapping("/api/staff/expenses")
    public List<ExpenseResponse> listExpenses(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @RequestParam(required = false) UUID branchId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        StaffContext context = requireView(sessionCookie);
        UUID activeBranchId = requireRequestedBranch(context, branchId);
        Map<UUID, String> categoryNames = categoryNameMap(context.businessId());
        return expenseService.listExpenses(context, activeBranchId, from, to).stream()
                .map(expense -> toResponse(expense, categoryNames))
                .toList();
    }

    @PostMapping("/api/staff/expenses")
    public ResponseEntity<ExpenseResponse> createExpense(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody CreateExpenseRequest request) {
        StaffContext context = requireManage(sessionCookie);
        UUID activeBranchId = requireRequestedBranch(context, request.branchId());
        Expense expense = expenseService.createExpense(
                context,
                activeBranchId,
                request.categoryId(),
                request.amountMinorUnits(),
                request.incurredAt(),
                request.vendor(),
                request.description(),
                request.receiptImageUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(expense, categoryNameMap(context.businessId())));
    }

    @PostMapping("/api/staff/expenses/{expenseId}")
    public ExpenseResponse updateExpense(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID expenseId,
            @Valid @RequestBody UpdateExpenseRequest request) {
        StaffContext context = requireManage(sessionCookie);
        Expense expense = expenseService.updateManualExpense(
                context,
                expenseId,
                request.categoryId(),
                request.amountMinorUnits(),
                request.incurredAt(),
                request.vendor(),
                request.description(),
                request.receiptImageUrl());
        return toResponse(expense, categoryNameMap(context.businessId()));
    }

    /**
     * The only way to read a receipt's bytes back - not exposed on any public /media/**
     * path (see MediaResourceConfig). Scoped to the caller's own
     * business/branch by ExpenseService#loadReceipt, same as every other per-id lookup.
     */
    @GetMapping("/api/staff/expenses/{expenseId}/receipt")
    public ResponseEntity<byte[]> getReceipt(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID expenseId) {
        StaffContext context = requireView(sessionCookie);
        LoadedMedia media = expenseService.loadReceipt(context, expenseId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(media.content());
    }

    @GetMapping("/api/staff/recurring-expense-templates")
    public List<RecurringExpenseTemplateResponse> listTemplates(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = requireView(sessionCookie);
        Map<UUID, String> categoryNames = categoryNameMap(context.businessId());
        return expenseService.listTemplates(context.businessId()).stream()
                .filter(template -> context.activeBranchId().equals(template.getBranchId()))
                .map(template -> toResponse(template, categoryNames))
                .toList();
    }

    @PostMapping("/api/staff/recurring-expense-templates")
    public ResponseEntity<RecurringExpenseTemplateResponse> createTemplate(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody CreateRecurringExpenseTemplateRequest request) {
        StaffContext context = requireManage(sessionCookie);
        UUID activeBranchId = requireRequestedBranch(context, request.branchId());
        RecurringExpenseTemplate template = expenseService.createTemplate(
                context,
                activeBranchId,
                request.categoryId(),
                request.amountMinorUnits(),
                request.vendor(),
                request.description(),
                request.dayOfMonth(),
                request.startDate(),
                request.endDate());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(template, categoryNameMap(context.businessId())));
    }

    @PostMapping("/api/staff/recurring-expense-templates/{templateId}/deactivate")
    public void deactivateTemplate(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID templateId) {
        StaffContext context = requireManage(sessionCookie);
        expenseService.deactivateTemplate(context, templateId);
    }

    @PostMapping("/api/staff/recurring-expense-templates/{templateId}/activate")
    public void activateTemplate(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID templateId) {
        StaffContext context = requireManage(sessionCookie);
        expenseService.activateTemplate(context, templateId);
    }

    @PostMapping("/api/staff/recurring-expense-templates/{templateId}")
    public RecurringExpenseTemplateResponse updateTemplate(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID templateId,
            @Valid @RequestBody UpdateRecurringExpenseTemplateRequest request) {
        StaffContext context = requireManage(sessionCookie);
        RecurringExpenseTemplate template = expenseService.updateTemplate(
                context,
                templateId,
                request.categoryId(),
                request.amountMinorUnits(),
                request.vendor(),
                request.description(),
                request.dayOfMonth(),
                request.startDate(),
                request.endDate());
        return toResponse(template, categoryNameMap(context.businessId()));
    }

    @DeleteMapping("/api/staff/recurring-expense-templates/{templateId}")
    public ResponseEntity<Void> deleteTemplate(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID templateId) {
        StaffContext context = requireManage(sessionCookie);
        expenseService.deleteTemplate(context, templateId);
        return ResponseEntity.noContent().build();
    }

    private StaffContext requireView(String sessionCookie) {
        return staffAuthService.resolveStaffContextForActiveBranch(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.EXPENSE_VIEW);
    }

    private StaffContext requireManage(String sessionCookie) {
        return staffAuthService.resolveStaffContextForActiveBranch(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.EXPENSE_MANAGE);
    }

    private UUID requireRequestedBranch(StaffContext context, UUID requestedBranchId) {
        if (requestedBranchId != null && !context.activeBranchId().equals(requestedBranchId)) {
            throw new StaffPermissionDeniedException("Not authorized for branch: " + requestedBranchId);
        }
        return context.activeBranchId();
    }

    private Map<UUID, String> categoryNameMap(UUID businessId) {
        return expenseService.listCategories(businessId).stream()
                .collect(Collectors.toMap(ExpenseCategory::getId, ExpenseCategory::getName, (a, b) -> a));
    }

    private static ExpenseCategoryResponse toResponse(ExpenseCategory category) {
        return new ExpenseCategoryResponse(category.getId(), category.getName(), category.isActive());
    }

    private static ExpenseResponse toResponse(Expense expense, Map<UUID, String> categoryNames) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getBranchId(),
                expense.getCategoryId(),
                categoryNames.get(expense.getCategoryId()),
                expense.getAmountMinorUnits(),
                expense.getIncurredAt(),
                expense.getVendor(),
                expense.getDescription(),
                expense.getReceiptImageUrl(),
                expense.getCreatedAt());
    }

    private static RecurringExpenseTemplateResponse toResponse(
            RecurringExpenseTemplate template, Map<UUID, String> categoryNames) {
        return new RecurringExpenseTemplateResponse(
                template.getId(),
                template.getBranchId(),
                template.getCategoryId(),
                categoryNames.get(template.getCategoryId()),
                template.getAmountMinorUnits(),
                template.getVendor(),
                template.getDescription(),
                template.getDayOfMonth(),
                template.getStartDate(),
                template.getEndDate(),
                template.isActive());
    }
}
