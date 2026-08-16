"use client";

import { useEffect, useState } from "react";
import { ApiError, formatPriceMinorUnits, getChainSalesReport, type ChainSalesReport } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import KpiCard from "@/components/ui/KpiCard";
import BarList from "@/components/ui/BarList";
import DateRangePresets, { presetRange, type DateRange } from "@/components/ui/DateRangePresets";
import Table from "@/components/ui/Table";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import tableStyles from "@/components/ui/Table.module.css";
import adminStyles from "@/styles/admin.module.css";
import styles from "../reports/reports.module.css";

/** Preserved for platform/future chain roles; current user-facing roles receive 403. */
export default function ChainSalesReportPage() {
  const [range, setRange] = useState<DateRange>(() => presetRange("today"));
  const [report, setReport] = useState<ChainSalesReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  function load(nextRange: DateRange) {
    getChainSalesReport(nextRange.from, nextRange.to)
      .then((data) => {
        setReport(data);
        setError(null);
      })
      .catch((err) => setError(err instanceof ApiError ? "Rapor yüklenemedi." : "Beklenmedik bir hata oluştu."))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load(range);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleRangeChange(nextRange: DateRange) {
    setRange(nextRange);
    setLoading(true);
    load(nextRange);
  }

  const branchesByGross = report ? [...report.branches].sort((a, b) => b.grossSalesMinorUnits - a.grossSalesMinorUnits) : [];

  return (
    <AppShell>
      <main className={adminStyles.page}>
        <PageHeader title="Zincir Satış Raporları" description="Tüm şubeler için karşılaştırmalı ciro ve sipariş özeti." />
        <DateRangePresets value={range} onChange={handleRangeChange} />
        {loading ? <TableSkeleton /> : error ? (
          <ErrorState message={error} onRetry={() => load(range)} />
        ) : !report ? null : (
          <>
            <div className={adminStyles.section}><KpiGrid report={report} /></div>
            <section className={adminStyles.section}>
              <h2 className={adminStyles.sectionTitle}>Şube Sıralaması (brüt satış)</h2>
              <BarList items={branchesByGross.map((branch) => ({
                key: branch.branchId,
                label: branch.branchName,
                value: branch.grossSalesMinorUnits,
                valueLabel: formatPriceMinorUnits(branch.grossSalesMinorUnits),
              }))} />
            </section>
            <section className={adminStyles.section}>
              <h2 className={adminStyles.sectionTitle}>Şube Karşılaştırma</h2>
              <Table>
                <thead><tr><th>Şube</th><th>Brüt satış</th><th>Net satış</th><th>Refund</th><th>Sipariş</th><th>Ortalama sepet</th></tr></thead>
                <tbody>{report.branches.map((branch) => (
                  <tr key={branch.branchId}>
                    <td className={tableStyles.primary}>{branch.branchName}</td>
                    <td>{formatPriceMinorUnits(branch.grossSalesMinorUnits)}</td>
                    <td>{formatPriceMinorUnits(branch.netSalesMinorUnits)}</td>
                    <td className={tableStyles.muted}>{formatPriceMinorUnits(branch.refundTotalMinorUnits)}</td>
                    <td>{branch.orderCount}</td>
                    <td>{formatPriceMinorUnits(branch.averageOrderValueMinorUnits)}</td>
                  </tr>
                ))}</tbody>
              </Table>
            </section>
          </>
        )}
      </main>
    </AppShell>
  );
}

function KpiGrid({ report }: { report: ChainSalesReport }) {
  return (
    <div className={styles.kpiGrid}>
      <KpiCard label="Toplam brüt satış" value={formatPriceMinorUnits(report.totalGrossSalesMinorUnits)} />
      <KpiCard label="Toplam net satış" value={formatPriceMinorUnits(report.totalNetSalesMinorUnits)} />
      <KpiCard label="Toplam refund" value={formatPriceMinorUnits(report.totalRefundMinorUnits)} tone={report.totalRefundMinorUnits > 0 ? "danger" : "neutral"} />
      <KpiCard label="Toplam sipariş" value={String(report.totalOrderCount)} />
    </div>
  );
}
