"use client";

import { useEffect, useState } from "react";
import {
  approveExpense,
  createExpense,
  createExpenseCategory,
  createRecurringExpenseTemplate,
  deactivateExpenseCategory,
  deactivateRecurringExpenseTemplate,
  formatPriceMinorUnits,
  listBranches,
  listExpenseCategories,
  listExpenses,
  listRecurringExpenseTemplates,
  me,
  rejectExpense,
  submitExpense,
  type Branch,
  type Expense,
  type ExpenseCategory,
  type RecurringExpenseTemplate,
  type StaffContext,
} from "@/lib/api";
import StaffNav from "@/components/layout/StaffNav";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import styles from "@/styles/admin.module.css";

const STATUS_LABELS: Record<string, string> = {
  DRAFT: "Taslak",
  SUBMITTED: "Onay Bekliyor",
  APPROVED: "Onaylandı",
  REJECTED: "Reddedildi",
};

function statusTone(status: string): "neutral" | "danger" | "success" {
  if (status === "APPROVED") return "success";
  if (status === "REJECTED") return "danger";
  return "neutral";
}

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

function firstDayOfMonthIsoDate(): string {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10);
}

/** Gap-analysis #10 (product-requirements.md Section 16): gider kategorileri, giderler, tekrarlayan gider şablonları. */
export default function ExpensesPage() {
  const [context, setContext] = useState<StaffContext | null>(null);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [categories, setCategories] = useState<ExpenseCategory[]>([]);
  const [templates, setTemplates] = useState<RecurringExpenseTemplate[]>([]);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [from, setFrom] = useState(firstDayOfMonthIsoDate());
  const [to, setTo] = useState(todayIsoDate());
  const [branchFilter, setBranchFilter] = useState<string>("");

  const [categoryName, setCategoryName] = useState("");
  const [expenseBranchId, setExpenseBranchId] = useState<string>("");
  const [expenseCategoryId, setExpenseCategoryId] = useState("");
  const [expenseAmount, setExpenseAmount] = useState("");
  const [expenseDate, setExpenseDate] = useState(todayIsoDate());
  const [expenseVendor, setExpenseVendor] = useState("");
  const [creatingExpense, setCreatingExpense] = useState(false);

  const [templateBranchId, setTemplateBranchId] = useState<string>("");
  const [templateCategoryId, setTemplateCategoryId] = useState("");
  const [templateAmount, setTemplateAmount] = useState("");
  const [templateDayOfMonth, setTemplateDayOfMonth] = useState("1");
  const [creatingTemplate, setCreatingTemplate] = useState(false);

  const isBusinessAdmin = context?.role === "BUSINESS_ADMIN" || context?.role === "PLATFORM_ADMIN";
  const accessibleBranches = isBusinessAdmin ? branches : branches.filter((branch) => context?.branchIds.includes(branch.id));

  async function reloadExpenses(branchId: string, fromDate: string, toDate: string) {
    try {
      const data = await listExpenses(branchId || null, fromDate, toDate);
      setExpenses(data);
    } catch {
      setError("Gider listesi yüklenemedi.");
    }
  }

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const [staffContext, branchList, categoryList, templateList] = await Promise.all([
          me(),
          listBranches(),
          listExpenseCategories(),
          listRecurringExpenseTemplates(),
        ]);
        if (cancelled) return;
        setContext(staffContext);
        setBranches(branchList);
        setCategories(categoryList);
        setTemplates(templateList);
        await reloadExpenses("", firstDayOfMonthIsoDate(), todayIsoDate());
      } catch {
        if (!cancelled) setError("Sayfa yüklenemedi.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  function handleFilterSubmit(event: React.FormEvent) {
    event.preventDefault();
    void reloadExpenses(branchFilter, from, to);
  }

  async function handleCreateCategory(event: React.FormEvent) {
    event.preventDefault();
    if (!categoryName.trim()) return;
    try {
      const category = await createExpenseCategory(categoryName.trim());
      setCategories((current) => [...current, category]);
      setCategoryName("");
    } catch {
      setError("Kategori oluşturulamadı.");
    }
  }

  async function handleDeactivateCategory(categoryId: string) {
    try {
      await deactivateExpenseCategory(categoryId);
      setCategories((current) => current.map((c) => (c.id === categoryId ? { ...c, active: false } : c)));
    } catch {
      setError("Kategori devre dışı bırakılamadı.");
    }
  }

  async function handleCreateExpense(event: React.FormEvent) {
    event.preventDefault();
    const amountMinorUnits = Math.round(Number(expenseAmount.replace(",", ".")) * 100);
    if (!expenseCategoryId || !Number.isFinite(amountMinorUnits) || amountMinorUnits <= 0) {
      setError("Kategori seçin ve geçerli bir tutar girin.");
      return;
    }
    setCreatingExpense(true);
    setError(null);
    try {
      await createExpense({
        branchId: expenseBranchId || null,
        categoryId: expenseCategoryId,
        amountMinorUnits,
        incurredAt: expenseDate,
        vendor: expenseVendor.trim() || null,
        description: null,
        receiptImageUrl: null,
      });
      setExpenseAmount("");
      setExpenseVendor("");
      await reloadExpenses(branchFilter, from, to);
    } catch {
      setError("Gider oluşturulamadı.");
    } finally {
      setCreatingExpense(false);
    }
  }

  async function handleSubmitExpense(expenseId: string) {
    setError(null);
    try {
      await submitExpense(expenseId);
      await reloadExpenses(branchFilter, from, to);
    } catch {
      setError("Gider gönderilemedi.");
    }
  }

  async function handleApprove(expenseId: string) {
    setError(null);
    try {
      await approveExpense(expenseId);
      await reloadExpenses(branchFilter, from, to);
    } catch {
      setError("Gider onaylanamadı.");
    }
  }

  async function handleReject(expenseId: string) {
    setError(null);
    try {
      await rejectExpense(expenseId);
      await reloadExpenses(branchFilter, from, to);
    } catch {
      setError("Gider reddedilemedi.");
    }
  }

  async function handleCreateTemplate(event: React.FormEvent) {
    event.preventDefault();
    const amountMinorUnits = Math.round(Number(templateAmount.replace(",", ".")) * 100);
    const dayOfMonth = Number(templateDayOfMonth);
    if (!templateCategoryId || !Number.isFinite(amountMinorUnits) || amountMinorUnits <= 0 || dayOfMonth < 1 || dayOfMonth > 31) {
      setError("Şablon için kategori, tutar ve geçerli bir gün (1-31) girin.");
      return;
    }
    setCreatingTemplate(true);
    setError(null);
    try {
      const template = await createRecurringExpenseTemplate({
        branchId: templateBranchId || null,
        categoryId: templateCategoryId,
        amountMinorUnits,
        vendor: null,
        description: null,
        dayOfMonth,
        startDate: todayIsoDate(),
        endDate: null,
      });
      setTemplates((current) => [...current, template]);
      setTemplateAmount("");
    } catch {
      setError("Tekrarlayan gider şablonu oluşturulamadı.");
    } finally {
      setCreatingTemplate(false);
    }
  }

  async function handleDeactivateTemplate(templateId: string) {
    try {
      await deactivateRecurringExpenseTemplate(templateId);
      setTemplates((current) => current.map((t) => (t.id === templateId ? { ...t, active: false } : t)));
    } catch {
      setError("Şablon devre dışı bırakılamadı.");
    }
  }

  function branchName(branchId: string | null): string {
    if (!branchId) return "İşletme geneli";
    return branches.find((b) => b.id === branchId)?.name ?? branchId;
  }

  return (
    <>
      <StaffNav />
      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>Gider Yönetimi</h1>
        </div>

        {error ? <p className={styles.error}>{error}</p> : null}

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Gider Kategorileri</h2>
          <form className={styles.form} onSubmit={handleCreateCategory}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="category-name">
                Yeni kategori
              </label>
              <input
                id="category-name"
                className={styles.input}
                value={categoryName}
                onChange={(event) => setCategoryName(event.target.value)}
                placeholder="ör. Kira, Elektrik, Malzeme"
              />
            </div>
            <Button type="submit">Ekle</Button>
          </form>
          <div className={styles.rowActions}>
            {categories
              .filter((category) => category.active)
              .map((category) => (
                <Badge key={category.id} tone="neutral">
                  {category.name}
                  {isBusinessAdmin ? (
                    <button
                      type="button"
                      onClick={() => handleDeactivateCategory(category.id)}
                      style={{ marginLeft: 6, border: "none", background: "none", cursor: "pointer", color: "inherit" }}
                    >
                      ×
                    </button>
                  ) : null}
                </Badge>
              ))}
          </div>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Yeni Gider</h2>
          <form className={styles.form} onSubmit={handleCreateExpense}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="expense-branch">
                Şube
              </label>
              <select
                id="expense-branch"
                className={styles.select}
                value={expenseBranchId}
                onChange={(event) => setExpenseBranchId(event.target.value)}
              >
                {isBusinessAdmin ? <option value="">İşletme geneli</option> : null}
                {accessibleBranches.map((branch) => (
                  <option key={branch.id} value={branch.id}>
                    {branch.name}
                  </option>
                ))}
              </select>
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="expense-category">
                Kategori
              </label>
              <select
                id="expense-category"
                className={styles.select}
                value={expenseCategoryId}
                onChange={(event) => setExpenseCategoryId(event.target.value)}
              >
                <option value="">Seçin</option>
                {categories
                  .filter((category) => category.active)
                  .map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
              </select>
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="expense-amount">
                Tutar (₺)
              </label>
              <input
                id="expense-amount"
                className={styles.input}
                value={expenseAmount}
                onChange={(event) => setExpenseAmount(event.target.value)}
                placeholder="0,00"
              />
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="expense-date">
                Tarih
              </label>
              <input
                id="expense-date"
                type="date"
                className={styles.input}
                value={expenseDate}
                onChange={(event) => setExpenseDate(event.target.value)}
              />
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="expense-vendor">
                Satıcı (opsiyonel)
              </label>
              <input
                id="expense-vendor"
                className={styles.input}
                value={expenseVendor}
                onChange={(event) => setExpenseVendor(event.target.value)}
              />
            </div>
            <Button type="submit" disabled={creatingExpense}>
              {creatingExpense ? "Oluşturuluyor…" : "Gider Ekle"}
            </Button>
          </form>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Giderler</h2>
          <form className={styles.form} onSubmit={handleFilterSubmit}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="filter-branch">
                Şube
              </label>
              <select
                id="filter-branch"
                className={styles.select}
                value={branchFilter}
                onChange={(event) => setBranchFilter(event.target.value)}
              >
                <option value="">Tümü</option>
                {accessibleBranches.map((branch) => (
                  <option key={branch.id} value={branch.id}>
                    {branch.name}
                  </option>
                ))}
              </select>
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="filter-from">
                Başlangıç
              </label>
              <input id="filter-from" type="date" className={styles.input} value={from} max={to} onChange={(event) => setFrom(event.target.value)} />
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="filter-to">
                Bitiş
              </label>
              <input id="filter-to" type="date" className={styles.input} value={to} min={from} onChange={(event) => setTo(event.target.value)} />
            </div>
            <Button type="submit">Uygula</Button>
          </form>

          <div className={styles.list}>
            {loading ? (
              <p className={styles.empty}>Yükleniyor…</p>
            ) : expenses.length === 0 ? (
              <p className={styles.empty}>Bu aralıkta gider yok.</p>
            ) : (
              expenses.map((expense) => (
                <div key={expense.id} className={styles.row}>
                  <div className={styles.rowMain}>
                    <span className={styles.rowTitle}>
                      {expense.categoryName ?? "—"} · {formatPriceMinorUnits(expense.amountMinorUnits)}
                    </span>
                    <span className={styles.rowMeta}>
                      {expense.incurredAt} · {branchName(expense.branchId)}
                      {expense.vendor ? ` · ${expense.vendor}` : ""}
                    </span>
                  </div>
                  <div className={styles.rowActions}>
                    <Badge tone={statusTone(expense.status)}>{STATUS_LABELS[expense.status] ?? expense.status}</Badge>
                    {expense.status === "DRAFT" ? (
                      <Button size="md" variant="ghost" onClick={() => handleSubmitExpense(expense.id)}>
                        Gönder
                      </Button>
                    ) : null}
                    {expense.status === "SUBMITTED" && isBusinessAdmin ? (
                      <>
                        <Button size="md" variant="secondary" onClick={() => handleApprove(expense.id)}>
                          Onayla
                        </Button>
                        <Button size="md" variant="ghost" onClick={() => handleReject(expense.id)}>
                          Reddet
                        </Button>
                      </>
                    ) : null}
                  </div>
                </div>
              ))
            )}
          </div>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Tekrarlayan Gider Şablonları</h2>
          <form className={styles.form} onSubmit={handleCreateTemplate}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="template-branch">
                Şube
              </label>
              <select
                id="template-branch"
                className={styles.select}
                value={templateBranchId}
                onChange={(event) => setTemplateBranchId(event.target.value)}
              >
                {isBusinessAdmin ? <option value="">İşletme geneli</option> : null}
                {accessibleBranches.map((branch) => (
                  <option key={branch.id} value={branch.id}>
                    {branch.name}
                  </option>
                ))}
              </select>
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="template-category">
                Kategori
              </label>
              <select
                id="template-category"
                className={styles.select}
                value={templateCategoryId}
                onChange={(event) => setTemplateCategoryId(event.target.value)}
              >
                <option value="">Seçin</option>
                {categories
                  .filter((category) => category.active)
                  .map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
              </select>
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="template-amount">
                Tutar (₺)
              </label>
              <input
                id="template-amount"
                className={styles.input}
                value={templateAmount}
                onChange={(event) => setTemplateAmount(event.target.value)}
                placeholder="0,00"
              />
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="template-day">
                Ayın günü
              </label>
              <input
                id="template-day"
                type="number"
                min={1}
                max={31}
                className={styles.input}
                value={templateDayOfMonth}
                onChange={(event) => setTemplateDayOfMonth(event.target.value)}
              />
            </div>
            <Button type="submit" disabled={creatingTemplate}>
              {creatingTemplate ? "Oluşturuluyor…" : "Şablon Ekle"}
            </Button>
          </form>

          <div className={styles.list}>
            {templates.filter((t) => t.active).length === 0 ? (
              <p className={styles.empty}>Aktif şablon yok.</p>
            ) : (
              templates
                .filter((t) => t.active)
                .map((template) => (
                  <div key={template.id} className={styles.row}>
                    <div className={styles.rowMain}>
                      <span className={styles.rowTitle}>
                        {template.categoryName ?? "—"} · {formatPriceMinorUnits(template.amountMinorUnits)}
                      </span>
                      <span className={styles.rowMeta}>
                        Her ayın {template.dayOfMonth}. günü · {branchName(template.branchId)}
                      </span>
                    </div>
                    <div className={styles.rowActions}>
                      <Button size="md" variant="ghost" onClick={() => handleDeactivateTemplate(template.id)}>
                        Devre Dışı Bırak
                      </Button>
                    </div>
                  </div>
                ))
            )}
          </div>
        </section>
      </main>
    </>
  );
}
