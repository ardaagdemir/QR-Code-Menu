"use client";

import { useEffect, useState } from "react";
import { getBranchComparison, type BranchComparisonRow } from "@/lib/api";
import StaffNav from "@/components/layout/StaffNav";
import styles from "@/styles/admin.module.css";

/**
 * Gap-analysis #7 (product-requirements.md Section 13.2): non-financial branch
 * comparison, deliberately scoped to order count + table-visit count in the last 24h -
 * revenue/refund comparisons belong to the future Reporting module (gap-analysis #8).
 */
export default function ChainComparisonPage() {
  const [rows, setRows] = useState<BranchComparisonRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getBranchComparison()
      .then(setRows)
      .catch(() => setError("Şube karşılaştırması yüklenemedi."))
      .finally(() => setLoading(false));
  }, []);

  return (
    <>
      <StaffNav />
      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>Şube Karşılaştırma</h1>
        </div>
        <p className={styles.rowMeta}>Son 24 saat, sipariş sayısı ve masa ziyareti sayısı.</p>

        {error ? <p className={styles.error}>{error}</p> : null}

        <section className={styles.section}>
          <div className={styles.list}>
            {loading ? (
              <p className={styles.empty}>Yükleniyor…</p>
            ) : rows.length === 0 ? (
              <p className={styles.empty}>Şube bulunamadı.</p>
            ) : (
              rows.map((row) => (
                <div key={row.branchId} className={styles.row}>
                  <div className={styles.rowMain}>
                    <span className={styles.rowTitle}>{row.branchName}</span>
                  </div>
                  <div className={styles.rowActions}>
                    <span className={styles.rowMeta}>Sipariş: {row.orderCount}</span>
                    <span className={styles.rowMeta}>Masa Ziyareti: {row.tableVisitCount}</span>
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
