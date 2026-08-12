"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ApiError, formatPriceMinorUnits, getChainSalesReport, type ChainSalesReport } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import Button from "@/components/ui/Button";
import adminStyles from "@/styles/admin.module.css";
import styles from "./reports.module.css";

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Gap-analysis #8 (product-requirements.md Section 13.2): BUSINESS_ADMIN'in zincir
 * görünümü - tüm şubeler için karşılaştırmalı brüt/net satış, refund, sipariş sayısı.
 * Permission.REPORT_CHAIN_VIEW yalnızca BUSINESS_ADMIN/PLATFORM_ADMIN'de olduğu için
 * bu ekran de nav'da yalnızca admin'e gösteriliyor (AppShell) - şube bazlı detay için
 * her satır /reports/{branchId}'ye bağlanıyor.
 */
export default function ChainSalesReportPage() {
  const [from, setFrom] = useState(todayIsoDate());
  const [to, setTo] = useState(todayIsoDate());
  const [report, setReport] = useState<ChainSalesReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  /** setState calls only happen inside the promise callbacks, never synchronously - safe to call from an effect body. */
  function applyReport(promise: Promise<ChainSalesReport>) {
    promise
      .then((data) => {
        setReport(data);
        setError(null);
      })
      .catch((err) => setError(err instanceof ApiError ? "Rapor yüklenemedi." : "Beklenmedik bir hata oluştu."))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    applyReport(getChainSalesReport(from, to));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setLoading(true);
    applyReport(getChainSalesReport(from, to));
  }

  return (
    <AppShell>
      <main className={adminStyles.page}>
        <div className={adminStyles.header}>
          <h1 className={adminStyles.title}>Satış Raporları</h1>
        </div>

        <form className={styles.filters} onSubmit={handleSubmit}>
          <div className={adminStyles.field}>
            <label className={adminStyles.label} htmlFor="from">
              Başlangıç
            </label>
            <input
              id="from"
              type="date"
              className={adminStyles.input}
              value={from}
              max={to}
              onChange={(event) => setFrom(event.target.value)}
            />
          </div>
          <div className={adminStyles.field}>
            <label className={adminStyles.label} htmlFor="to">
              Bitiş
            </label>
            <input
              id="to"
              type="date"
              className={adminStyles.input}
              value={to}
              min={from}
              onChange={(event) => setTo(event.target.value)}
            />
          </div>
          <Button type="submit">Uygula</Button>
        </form>

        {error ? <p className={adminStyles.error}>{error}</p> : null}

        {loading ? (
          <p className={adminStyles.empty}>Yükleniyor…</p>
        ) : !report ? null : (
          <>
            <div className={styles.statGrid}>
              <StatCard label="Toplam brüt satış" value={formatPriceMinorUnits(report.totalGrossSalesMinorUnits)} />
              <StatCard label="Toplam net satış" value={formatPriceMinorUnits(report.totalNetSalesMinorUnits)} />
              <StatCard label="Toplam refund" value={formatPriceMinorUnits(report.totalRefundMinorUnits)} />
              <StatCard label="Toplam sipariş" value={String(report.totalOrderCount)} />
            </div>

            <section className={adminStyles.section}>
              <h2 className={adminStyles.sectionTitle}>Şube Karşılaştırma</h2>
              <div className={styles.tableWrap}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>Şube</th>
                      <th>Brüt satış</th>
                      <th>Net satış</th>
                      <th>Refund</th>
                      <th>Sipariş</th>
                      <th>Ortalama sepet</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.branches.map((branch) => (
                      <tr key={branch.branchId}>
                        <td>
                          <Link href={`/reports/${branch.branchId}`}>{branch.branchName}</Link>
                        </td>
                        <td>{formatPriceMinorUnits(branch.grossSalesMinorUnits)}</td>
                        <td>{formatPriceMinorUnits(branch.netSalesMinorUnits)}</td>
                        <td>{formatPriceMinorUnits(branch.refundTotalMinorUnits)}</td>
                        <td>{branch.orderCount}</td>
                        <td>{formatPriceMinorUnits(branch.averageOrderValueMinorUnits)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          </>
        )}
      </main>
    </AppShell>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className={styles.statCard}>
      <span className={styles.statLabel}>{label}</span>
      <span className={styles.statValue}>{value}</span>
    </div>
  );
}
