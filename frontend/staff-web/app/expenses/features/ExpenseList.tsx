"use client";

import { useEffect, useState } from "react";
import { formatPriceMinorUnits, listExpenses, type Expense } from "@/lib/api";
import { localIsoDate } from "@/lib/time";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Button from "@/components/ui/Button";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
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
  refreshToken: number;
};

/** Gider Yönetimi'nin manuel gider listesi (product-requirements.md Section 16). */
export default function ExpenseList({ refreshToken }: Props) {
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [from, setFrom] = useState(firstDayOfMonthIsoDate());
  const [to, setTo] = useState(todayIsoDate());
  const [appliedFilters, setAppliedFilters] = useState({ from: firstDayOfMonthIsoDate(), to: todayIsoDate() });

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
              {expenses.map((expense) => {
                return (
                  <tr key={expense.id}>
                    <td className={tableStyles.muted}>{expense.categoryName ?? "—"}</td>
                    <td className={expenseStyles.templateAmount}>{formatPriceMinorUnits(expense.amountMinorUnits)}</td>
                    <td className={tableStyles.muted}>{formatExpenseDate(expense.incurredAt)}</td>
                    <td className={tableStyles.muted}>{expense.vendor?.trim() || "—"}</td>
                    <td className={`${expenseStyles.actionsCell} ${tableStyles.muted}`}>—</td>
                  </tr>
                );
              })}
            </tbody>
          </Table>
        </div>
      )}
    </section>
  );
}
