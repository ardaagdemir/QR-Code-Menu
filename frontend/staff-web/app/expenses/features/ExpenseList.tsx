"use client";

import { useEffect, useState } from "react";
import {
  approveExpense,
  formatPriceMinorUnits,
  listExpenses,
  rejectExpense,
  submitExpense,
  type Branch,
  type Expense,
} from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import { useToast } from "@/components/ui/ToastProvider";
import tableStyles from "@/components/ui/Table.module.css";
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

type Props = {
  accessibleBranches: Branch[];
  isBusinessAdmin: boolean;
  refreshToken: number;
};

/** Gider Yönetimi'nin gider listesi + onay akışı paneli (product-requirements.md Section 16). */
export default function ExpenseList({ accessibleBranches, isBusinessAdmin, refreshToken }: Props) {
  const { showToast } = useToast();

  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyExpenseId, setBusyExpenseId] = useState<string | null>(null);

  const [from, setFrom] = useState(firstDayOfMonthIsoDate());
  const [to, setTo] = useState(todayIsoDate());
  const [branchFilter, setBranchFilter] = useState("");
  const [appliedFilters, setAppliedFilters] = useState({ from: firstDayOfMonthIsoDate(), to: todayIsoDate(), branchFilter: "" });

  function load() {
    listExpenses(appliedFilters.branchFilter || null, appliedFilters.from, appliedFilters.to)
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
    setAppliedFilters({ from, to, branchFilter });
  }

  async function handleSubmitExpense(expenseId: string) {
    setBusyExpenseId(expenseId);
    try {
      await submitExpense(expenseId);
      load();
      showToast("Gider gönderildi.", "success");
    } catch {
      showToast("Gider gönderilemedi.", "error");
    } finally {
      setBusyExpenseId(null);
    }
  }

  async function handleApprove(expenseId: string) {
    setBusyExpenseId(expenseId);
    try {
      await approveExpense(expenseId);
      load();
      showToast("Gider onaylandı.", "success");
    } catch {
      showToast("Gider onaylanamadı.", "error");
    } finally {
      setBusyExpenseId(null);
    }
  }

  async function handleReject(expenseId: string) {
    setBusyExpenseId(expenseId);
    try {
      await rejectExpense(expenseId);
      load();
      showToast("Gider reddedildi.", "success");
    } catch {
      showToast("Gider reddedilemedi.", "error");
    } finally {
      setBusyExpenseId(null);
    }
  }

  function branchName(branchId: string | null): string {
    if (!branchId) return "İşletme geneli";
    return accessibleBranches.find((b) => b.id === branchId)?.name ?? branchId;
  }

  return (
    <section className={styles.section}>
      <PageHeader title="Giderler" />

      <form className={styles.form} onSubmit={handleFilterSubmit}>
        <FormField label="Şube">
          {(controlProps) => (
            <Select {...controlProps} value={branchFilter} onChange={(event) => setBranchFilter(event.target.value)}>
              <option value="">Tümü</option>
              {accessibleBranches.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.name}
                </option>
              ))}
            </Select>
          )}
        </FormField>
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
        <Table>
          <thead>
            <tr>
              <th>Kategori / Tutar</th>
              <th>Tarih</th>
              <th>Şube</th>
              <th>Satıcı</th>
              <th>Durum</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {expenses.map((expense) => (
              <tr key={expense.id}>
                <td className={tableStyles.primary}>
                  {expense.categoryName ?? "—"} · {formatPriceMinorUnits(expense.amountMinorUnits)}
                </td>
                <td className={tableStyles.muted}>{expense.incurredAt}</td>
                <td className={tableStyles.muted}>{branchName(expense.branchId)}</td>
                <td className={tableStyles.muted}>{expense.vendor ?? "—"}</td>
                <td>
                  <Badge tone={statusTone(expense.status)}>{STATUS_LABELS[expense.status] ?? expense.status}</Badge>
                </td>
                <td>
                  <div className={tableStyles.actions}>
                    {expense.status === "DRAFT" ? (
                      <Button size="md" variant="ghost" disabled={busyExpenseId === expense.id} onClick={() => handleSubmitExpense(expense.id)}>
                        Gönder
                      </Button>
                    ) : null}
                    {expense.status === "SUBMITTED" && isBusinessAdmin ? (
                      <>
                        <Button size="md" variant="secondary" disabled={busyExpenseId === expense.id} onClick={() => handleApprove(expense.id)}>
                          Onayla
                        </Button>
                        <Button size="md" variant="ghost" disabled={busyExpenseId === expense.id} onClick={() => handleReject(expense.id)}>
                          Reddet
                        </Button>
                      </>
                    ) : null}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </section>
  );
}
