"use client";

import { useEffect, useState } from "react";
import { getBranchComparison, type BranchComparisonRow } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";

/** Preserved for platform/future chain roles; current user-facing roles receive 403. */
export default function ChainComparisonPage() {
  const [rows, setRows] = useState<BranchComparisonRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  function load() {
    getBranchComparison()
      .then((data) => {
        setRows(data);
        setError(null);
      })
      .catch(() => setError("Şube karşılaştırması yüklenemedi."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader title="Şube Karşılaştırma" description="Son 24 saat, sipariş ve masa ziyareti sayısı." />
        {loading ? <TableSkeleton /> : error ? (
          <ErrorState message={error} onRetry={load} />
        ) : rows.length === 0 ? (
          <EmptyState title="Şube bulunamadı" />
        ) : (
          <Table>
            <thead><tr><th>Şube</th><th className={tableStyles.numeric}>Sipariş</th><th className={tableStyles.numeric}>Masa Ziyareti</th></tr></thead>
            <tbody>{rows.map((row) => (
              <tr key={row.branchId}>
                <td className={tableStyles.primary}>{row.branchName}</td>
                <td className={tableStyles.numeric}>{row.orderCount}</td>
                <td className={tableStyles.numeric}>{row.tableVisitCount}</td>
              </tr>
            ))}</tbody>
          </Table>
        )}
      </main>
    </AppShell>
  );
}
