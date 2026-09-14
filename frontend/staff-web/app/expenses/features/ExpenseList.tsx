"use client";

import { useEffect, useId, useState } from "react";
import { Pencil, Trash2 } from "lucide-react";
import {
  deleteExpense,
  formatPriceMinorUnits,
  listExpenses,
  updateExpense,
  uploadReceiptImage,
  type Expense,
  type ExpenseCategory,
} from "@/lib/api";
import { branchIsoDate } from "@/lib/time";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import FileUploadField from "@/components/ui/FileUploadField";
import { useToast } from "@/components/ui/ToastProvider";
import { formatAmountInput, formatAmountMinorUnitsForInput, parseAmountInputToMinorUnits } from "./expenseAmount";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";
import expenseStyles from "../expenses.module.css";

function todayIsoDate(timeZone: string | null): string {
  return branchIsoDate(timeZone);
}

function firstDayOfMonthIsoDate(timeZone: string | null): string {
  const [year, month] = branchIsoDate(timeZone).split("-");
  return `${year}-${month}-01`;
}

function formatExpenseDate(isoDate: string): string {
  return new Intl.DateTimeFormat("tr-TR", {
    day: "numeric",
    month: "short",
    year: "numeric",
  })
    .format(new Date(`${isoDate.slice(0, 10)}T12:00:00`))
    .replaceAll(".", "");
}

type Props = {
  categories: ExpenseCategory[];
  refreshToken: number;
  branchTimeZone: string | null;
};

/** Gider Yönetimi'nin manuel gider listesi (product-requirements.md Section 16). */
export default function ExpenseList({ categories, refreshToken, branchTimeZone }: Props) {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [from, setFrom] = useState(firstDayOfMonthIsoDate(branchTimeZone));
  const [to, setTo] = useState(todayIsoDate(branchTimeZone));
  const [appliedFilters, setAppliedFilters] = useState({
    from: firstDayOfMonthIsoDate(branchTimeZone),
    to: todayIsoDate(branchTimeZone),
  });
  const [filtersAnchoredToBranch, setFiltersAnchoredToBranch] = useState(branchTimeZone !== null);

  const [editingExpense, setEditingExpense] = useState<Expense | null>(null);
  const [categoryId, setCategoryId] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(todayIsoDate(branchTimeZone));
  const [vendor, setVendor] = useState("");
  const [receiptImageUrl, setReceiptImageUrl] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const [deleteTarget, setDeleteTarget] = useState<Expense | null>(null);
  const [deleting, setDeleting] = useState(false);

  function load() {
    listExpenses(appliedFilters.from, appliedFilters.to)
      .then((data) => {
        setExpenses(data);
        setError(null);
      })
      .catch(() => setError("Gider listesi yüklenemedi."))
      .finally(() => setLoading(false));
  }

  useEffect(load, [appliedFilters, refreshToken]);

  // Placeholder filters above render with the device's own date (see lib/time.ts) for an
  // instant first paint; once `me()` resolves the active branch's real timezone, re-anchor
  // the default range to it exactly once - matching Özet/Kasa/Raporlar - so an expense dated
  // "today" here means the same calendar day the report screen uses. Adjusted during render
  // (not in an effect) per https://react.dev/learn/you-might-not-need-an-effect.
  if (!filtersAnchoredToBranch && branchTimeZone !== null) {
    setFiltersAnchoredToBranch(true);
    const branchFrom = firstDayOfMonthIsoDate(branchTimeZone);
    const branchTo = todayIsoDate(branchTimeZone);
    setFrom(branchFrom);
    setTo(branchTo);
    setAppliedFilters({ from: branchFrom, to: branchTo });
  }

  function handleFilterSubmit(event: React.FormEvent) {
    event.preventDefault();
    setLoading(true);
    setAppliedFilters({ from, to });
  }

  function openEditDialog(expense: Expense) {
    setEditingExpense(expense);
    setCategoryId(expense.categoryId);
    setAmount(formatAmountMinorUnitsForInput(expense.amountMinorUnits));
    setDate(expense.incurredAt.slice(0, 10));
    setVendor(expense.vendor ?? "");
    setReceiptImageUrl(expense.receiptImageUrl);
    setFormError(null);
  }

  function closeEditDialog() {
    if (!saving) {
      setEditingExpense(null);
    }
  }

  async function handleSaveEdit(event: React.FormEvent) {
    event.preventDefault();
    if (!editingExpense) {
      return;
    }
    const amountMinorUnits = parseAmountInputToMinorUnits(amount);
    if (!categoryId || amountMinorUnits === null || amountMinorUnits <= 0) {
      setFormError("Kategori seçin ve geçerli bir tutar girin.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      await updateExpense(editingExpense.id, {
        categoryId,
        amountMinorUnits,
        incurredAt: date,
        vendor: vendor.trim() || null,
        description: editingExpense.description,
        receiptImageUrl,
      });
      setEditingExpense(null);
      load();
      showToast("Gider güncellendi.", "success");
    } catch {
      setFormError("Gider güncellenemedi.");
    } finally {
      setSaving(false);
    }
  }

  async function handleConfirmDelete() {
    if (!deleteTarget) {
      return;
    }
    setDeleting(true);
    try {
      await deleteExpense(deleteTarget.id);
      setExpenses((current) => current.filter((expense) => expense.id !== deleteTarget.id));
      showToast("Gider kaydı silindi.", "success");
    } catch {
      showToast("Gider kaydı silinemedi.", "error");
    } finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
  }

  const activeCategories = categories.filter((category) => category.active || category.id === categoryId);

  return (
    <section className={`${styles.section} ${styles.panel}`}>
      <PageHeader title="Gider Listesi" description="Seçili tarih aralığındaki manuel gider kayıtlarını izleyin." />

      <form className={expenseStyles.filterToolbar} onSubmit={handleFilterSubmit}>
        <FormField label="Başlangıç">
          {(controlProps) => <Input {...controlProps} type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} />}
        </FormField>
        <FormField label="Bitiş">
          {(controlProps) => <Input {...controlProps} type="date" value={to} min={from} onChange={(event) => setTo(event.target.value)} />}
        </FormField>
        <Button type="submit">Uygula</Button>
      </form>

      {loading ? (
        <TableSkeleton />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : expenses.length === 0 ? (
        <EmptyState title="Bu aralıkta gider yok" />
      ) : (
        <div className={expenseStyles.expenseTable}>
          <Table>
            <thead>
              <tr>
                <th>Kategori</th>
                <th>Tutar</th>
                <th>Tarih</th>
                <th>Satıcı</th>
                <th className={expenseStyles.actionsHeader}>İşlemler</th>
              </tr>
            </thead>
            <tbody>
              {expenses.map((expense) => (
                <tr key={expense.id}>
                  <td className={tableStyles.muted}>{expense.categoryName ?? "—"}</td>
                  <td className={expenseStyles.templateAmount}>{formatPriceMinorUnits(expense.amountMinorUnits)}</td>
                  <td className={tableStyles.muted}>{formatExpenseDate(expense.incurredAt)}</td>
                  <td className={tableStyles.muted}>{expense.vendor?.trim() || "—"}</td>
                  <td className={expenseStyles.actionsCell}>
                    <div className={tableStyles.actions}>
                      <Button size="sm" variant="secondary" onClick={() => openEditDialog(expense)}>
                        <Pencil size={13} aria-hidden="true" /> Düzenle
                      </Button>
                      <Button size="sm" variant="danger" onClick={() => setDeleteTarget(expense)}>
                        <Trash2 size={13} aria-hidden="true" /> Sil
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        </div>
      )}

      {editingExpense ? (
        <Dialog onClose={closeEditDialog} labelledBy={dialogTitleId}>
          <h2 id={dialogTitleId} className={styles.sectionTitle}>
            Gideri Düzenle
          </h2>
          <form className={`${styles.section} ${expenseStyles.templateForm}`} onSubmit={handleSaveEdit}>
            <FormField label="Kategori" required>
              {(controlProps) => (
                <Select {...controlProps} value={categoryId} onChange={(event) => setCategoryId(event.target.value)} required>
                  <option value="">Seçin</option>
                  {activeCategories.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </Select>
              )}
            </FormField>
            <FormField label="Tutar (₺)" required>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  inputMode="decimal"
                  value={amount}
                  onChange={(event) => setAmount(event.target.value)}
                  onBlur={() => setAmount((current) => formatAmountInput(current))}
                  placeholder="0,00"
                  required
                />
              )}
            </FormField>
            <FormField label="Tarih">
              {(controlProps) => <Input {...controlProps} type="date" value={date} onChange={(event) => setDate(event.target.value)} />}
            </FormField>
            <FormField label="Satıcı / Firma (opsiyonel)">
              {(controlProps) => <Input {...controlProps} value={vendor} onChange={(event) => setVendor(event.target.value)} />}
            </FormField>
            <FileUploadField
              label="Fiş/fatura (opsiyonel)"
              hint="JPEG, PNG, WEBP veya PDF - en fazla 10MB."
              accept="image/jpeg,image/png,image/webp,application/pdf"
              value={receiptImageUrl}
              onChange={setReceiptImageUrl}
              upload={uploadReceiptImage}
              previewAsImage={false}
            />

            {formError ? <ErrorState message={formError} /> : null}

            <div className={expenseStyles.formActions}>
              <Button variant="secondary" onClick={closeEditDialog} disabled={saving}>
                Vazgeç
              </Button>
              <Button type="submit" disabled={saving}>
                {saving ? "Kaydediliyor…" : "Değişiklikleri Kaydet"}
              </Button>
            </div>
          </form>
        </Dialog>
      ) : null}

      {deleteTarget ? (
        <ConfirmDialog
          title="Kaydı Sil"
          message="Bu gider kaydı silinecek ve gider raporlarına dahil edilmeyecek. Bu işlem geri alınamaz."
          confirmLabel="Kaydı Sil"
          tone="danger"
          confirmLoading={deleting}
          onConfirm={handleConfirmDelete}
          onCancel={() => setDeleteTarget(null)}
        />
      ) : null}
    </section>
  );
}
