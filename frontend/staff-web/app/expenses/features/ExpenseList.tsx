"use client";

import { useEffect, useId, useState } from "react";
import { Pencil, Ban } from "lucide-react";
import {
  cancelExpense,
  formatPriceMinorUnits,
  listExpenses,
  updateExpense,
  uploadReceiptImage,
  type Expense,
  type ExpenseCategory,
} from "@/lib/api";
import { localIsoDate } from "@/lib/time";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Badge from "@/components/ui/Badge";
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

function todayIsoDate(): string {
  return localIsoDate();
}

function firstDayOfMonthIsoDate(): string {
  const now = new Date();
  return localIsoDate(new Date(now.getFullYear(), now.getMonth(), 1));
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
};

/** Gider Yönetimi'nin manuel gider listesi (product-requirements.md Section 16). */
export default function ExpenseList({ categories, refreshToken }: Props) {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [from, setFrom] = useState(firstDayOfMonthIsoDate());
  const [to, setTo] = useState(todayIsoDate());
  const [appliedFilters, setAppliedFilters] = useState({ from: firstDayOfMonthIsoDate(), to: todayIsoDate() });

  const [editingExpense, setEditingExpense] = useState<Expense | null>(null);
  const [categoryId, setCategoryId] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(todayIsoDate());
  const [vendor, setVendor] = useState("");
  const [receiptImageUrl, setReceiptImageUrl] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const [cancelTarget, setCancelTarget] = useState<Expense | null>(null);
  const [cancelling, setCancelling] = useState(false);

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

  async function handleConfirmCancel() {
    if (!cancelTarget) {
      return;
    }
    setCancelling(true);
    try {
      await cancelExpense(cancelTarget.id);
      load();
      showToast("Gider kaydı iptal edildi.", "success");
    } catch {
      showToast("Gider kaydı iptal edilemedi.", "error");
    } finally {
      setCancelling(false);
      setCancelTarget(null);
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
                <th>Durum</th>
                <th className={expenseStyles.actionsHeader}>İşlemler</th>
              </tr>
            </thead>
            <tbody>
              {expenses.map((expense) => {
                const cancelled = expense.cancelledAt !== null;
                return (
                  <tr key={expense.id}>
                    <td className={tableStyles.muted}>{expense.categoryName ?? "—"}</td>
                    <td className={expenseStyles.templateAmount}>{formatPriceMinorUnits(expense.amountMinorUnits)}</td>
                    <td className={tableStyles.muted}>{formatExpenseDate(expense.incurredAt)}</td>
                    <td className={tableStyles.muted}>{expense.vendor?.trim() || "—"}</td>
                    <td>
                      <Badge tone={cancelled ? "neutral" : "success"}>{cancelled ? "İptal Edildi" : "Kayıtlı"}</Badge>
                    </td>
                    <td className={expenseStyles.actionsCell}>
                      {cancelled ? (
                        <span className={tableStyles.muted}>—</span>
                      ) : (
                        <div className={tableStyles.actions}>
                          <Button size="md" variant="ghost" onClick={() => openEditDialog(expense)}>
                            <Pencil size={15} aria-hidden="true" /> Düzenle
                          </Button>
                          <Button
                            className={expenseStyles.dangerAction}
                            size="md"
                            variant="ghost"
                            onClick={() => setCancelTarget(expense)}
                          >
                            <Ban size={15} aria-hidden="true" /> Kaydı İptal Et
                          </Button>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
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

      {cancelTarget ? (
        <ConfirmDialog
          title="Kaydı İptal Et"
          message="Bu gider kaydı iptal edilecek. Kayıt listede görünmeye devam eder ama gider raporlarına ve Net Sonuç hesabına dahil edilmez. Bu işlem geri alınamaz."
          confirmLabel="Kaydı İptal Et"
          tone="danger"
          confirmLoading={cancelling}
          onConfirm={handleConfirmCancel}
          onCancel={() => setCancelTarget(null)}
        />
      ) : null}
    </section>
  );
}
