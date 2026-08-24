"use client";

import { useEffect, useState } from "react";
import { listExpenseCategories, me, type ExpenseCategory, type StaffContext } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import ErrorState from "@/components/ui/ErrorState";
import ExpenseCategories from "./features/ExpenseCategories";
import ExpenseForm from "./features/ExpenseForm";
import ExpenseList from "./features/ExpenseList";
import RecurringTemplates from "./features/RecurringTemplates";
import styles from "@/styles/admin.module.css";
import expenseStyles from "./expenses.module.css";

/**
 * Gap-analysis #10 (product-requirements.md Section 16): gider kategorileri, giderler,
 * tekrarlayan gider şablonları. Bölüm 19.3 "büyük ... expenses sayfaları feature/
 * component parçalarına ayrılır": bu sayfa yalnızca ortak context/branch/kategori
 * state'ini tutan bir orkestratör, her alt bölüm kendi feature component'inde.
 */
export default function ExpensesPage() {
  const [context, setContext] = useState<StaffContext | null>(null);
  const [categories, setCategories] = useState<ExpenseCategory[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [expenseRefreshToken, setExpenseRefreshToken] = useState(0);

  useEffect(() => {
    Promise.all([me(), listExpenseCategories()])
      .then(([staffContext, categoryList]) => {
        setContext(staffContext);
        setCategories(categoryList);
      })
      .catch(() => setError("Sayfa yüklenemedi."));
  }, []);

  const isBusinessAdmin = context?.role === "BUSINESS_ADMIN" || context?.role === "PLATFORM_ADMIN";
  const branchTimeZone = context?.activeBranchTimeZone ?? null;

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader title="Giderler" description="Manuel gider kayıtlarını, kategorileri ve tekrarlayan ödemeleri yönetin." />

        {error ? <ErrorState message={error} /> : null}

        <div className={expenseStyles.overviewGrid}>
          <ExpenseCategories
            categories={categories}
            isBusinessAdmin={isBusinessAdmin}
            onCategoryCreated={(category) => setCategories((current) => [...current, category])}
            onCategoryDeactivated={(categoryId) =>
              setCategories((current) => current.map((c) => (c.id === categoryId ? { ...c, active: false } : c)))
            }
          />

          <ExpenseForm
            categories={categories}
            branchTimeZone={branchTimeZone}
            onCreated={() => setExpenseRefreshToken((current) => current + 1)}
          />
        </div>

        <ExpenseList categories={categories} refreshToken={expenseRefreshToken} branchTimeZone={branchTimeZone} />

        <RecurringTemplates categories={categories} branchTimeZone={branchTimeZone} />
      </main>
    </AppShell>
  );
}
