"use client";

import { useEffect, useState } from "react";
import { listBranches, listExpenseCategories, me, type Branch, type ExpenseCategory, type StaffContext } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import ErrorState from "@/components/ui/ErrorState";
import ExpenseCategories from "./features/ExpenseCategories";
import ExpenseForm from "./features/ExpenseForm";
import ExpenseList from "./features/ExpenseList";
import RecurringTemplates from "./features/RecurringTemplates";
import styles from "@/styles/admin.module.css";

/**
 * Gap-analysis #10 (product-requirements.md Section 16): gider kategorileri, giderler,
 * tekrarlayan gider şablonları. Bölüm 19.3 "büyük ... expenses sayfaları feature/
 * component parçalarına ayrılır": bu sayfa yalnızca ortak context/branch/kategori
 * state'ini tutan bir orkestratör, her alt bölüm kendi feature component'inde.
 */
export default function ExpensesPage() {
  const [context, setContext] = useState<StaffContext | null>(null);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [categories, setCategories] = useState<ExpenseCategory[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [expenseRefreshToken, setExpenseRefreshToken] = useState(0);

  useEffect(() => {
    Promise.all([me(), listBranches(), listExpenseCategories()])
      .then(([staffContext, branchList, categoryList]) => {
        setContext(staffContext);
        setBranches(branchList);
        setCategories(categoryList);
      })
      .catch(() => setError("Sayfa yüklenemedi."));
  }, []);

  const isBusinessAdmin = context?.role === "BUSINESS_ADMIN" || context?.role === "PLATFORM_ADMIN";
  const accessibleBranches = isBusinessAdmin ? branches : branches.filter((branch) => context?.branchIds.includes(branch.id));

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader title="Gider Yönetimi" />

        {error ? <ErrorState message={error} /> : null}

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
          accessibleBranches={accessibleBranches}
          isBusinessAdmin={isBusinessAdmin}
          onCreated={() => setExpenseRefreshToken((current) => current + 1)}
        />

        <ExpenseList accessibleBranches={accessibleBranches} isBusinessAdmin={isBusinessAdmin} refreshToken={expenseRefreshToken} />

        <RecurringTemplates categories={categories} accessibleBranches={accessibleBranches} isBusinessAdmin={isBusinessAdmin} />
      </main>
    </AppShell>
  );
}
