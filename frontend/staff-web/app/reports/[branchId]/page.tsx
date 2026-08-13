"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import {
  ApiError,
  downloadBranchDailyCloseExcel,
  formatPriceMinorUnits,
  generateDailyCloseFinal,
  getBranchSalesReport,
  getDailyCloseReports,
  getOperatingResult,
  getOwnerNotifications,
  resendOwnerNotifications,
  type BranchSalesReport,
  type DailyCloseReport,
  type OperatingResult,
  type OwnerNotificationLog,
} from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import KpiCard from "@/components/ui/KpiCard";
import BarList from "@/components/ui/BarList";
import DateRangePresets, { presetRange, type DateRange } from "@/components/ui/DateRangePresets";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import tableStyles from "@/components/ui/Table.module.css";
import adminStyles from "@/styles/admin.module.css";
import styles from "../reports.module.css";

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Gap-analysis #8 (product-requirements.md Section 13.1) + UI/UX Productization Gate
 * Adım 6 (Bölüm 19.3 "Raporlama"): KPI cards, hızlı tarih presetleri, gelir trendi
 * (gün sonu kapanışlarından türetilen günlük brüt satış bar'ı), ürün/kategori ranking
 * bar'ları, saatlik dağılım ve refund etkisi aynı bilgi hiyerarşisinde. CASHIER/
 * BRANCH_MANAGER/BUSINESS_ADMIN (Permission.REPORT_VIEW) kendi şubeleri için görebilir.
 */
export default function BranchReportPage() {
  const params = useParams<{ branchId: string }>();
  const branchId = params.branchId;

  const [range, setRange] = useState<DateRange>(() => presetRange("today"));
  const [report, setReport] = useState<BranchSalesReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [dailyCloseReports, setDailyCloseReports] = useState<DailyCloseReport[]>([]);
  const [closingToday, setClosingToday] = useState(false);
  const [dailyCloseError, setDailyCloseError] = useState<string | null>(null);
  const [notificationsByReport, setNotificationsByReport] = useState<Record<string, OwnerNotificationLog[]>>({});
  const [resendingReportId, setResendingReportId] = useState<string | null>(null);

  const [operatingResult, setOperatingResult] = useState<OperatingResult | null>(null);

  function loadReport(nextRange: DateRange) {
    getBranchSalesReport(branchId, nextRange.from, nextRange.to)
      .then((data) => {
        setReport(data);
        setError(null);
      })
      .catch((err) => setError(err instanceof ApiError ? "Rapor yüklenemedi." : "Beklenmedik bir hata oluştu."))
      .finally(() => setLoading(false));
  }

  function reloadDailyClose(nextRange: DateRange) {
    getDailyCloseReports(branchId, nextRange.from, nextRange.to)
      .then((data) => {
        setDailyCloseReports(data);
        void reloadNotifications(data);
      })
      .catch(() => setDailyCloseReports([]));
  }

  /** Gap-analysis #11: one lookup per FINAL row - PREVIEW rows never had a notification dispatched. */
  async function reloadNotifications(rows: DailyCloseReport[]) {
    const finalRows = rows.filter((row) => row.status === "FINAL");
    const entries = await Promise.all(
      finalRows.map(async (row) => [row.id, await getOwnerNotifications(branchId, row.id).catch(() => [])] as const),
    );
    setNotificationsByReport(Object.fromEntries(entries));
  }

  function handleResend(reportId: string) {
    setResendingReportId(reportId);
    setDailyCloseError(null);
    resendOwnerNotifications(branchId, reportId)
      .then((logs) => setNotificationsByReport((prev) => ({ ...prev, [reportId]: logs })))
      .catch(() => setDailyCloseError("Bildirim yeniden gönderilemedi."))
      .finally(() => setResendingReportId(null));
  }

  function reloadOperatingResult(nextRange: DateRange) {
    getOperatingResult(branchId, nextRange.from, nextRange.to)
      .then((data) => setOperatingResult(data))
      .catch(() => setOperatingResult(null));
  }

  useEffect(() => {
    loadReport(range);
    reloadDailyClose(range);
    reloadOperatingResult(range);
    // Only re-fetch automatically when the branch changes - date range changes are applied via handleRangeChange.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId]);

  function handleRangeChange(nextRange: DateRange) {
    setRange(nextRange);
    setLoading(true);
    loadReport(nextRange);
    reloadDailyClose(nextRange);
    reloadOperatingResult(nextRange);
  }

  function handleCloseToday() {
    setClosingToday(true);
    setDailyCloseError(null);
    generateDailyCloseFinal(branchId, todayIsoDate())
      .then(() => reloadDailyClose(range))
      .catch(() => setDailyCloseError("Gün sonu kapatılamadı."))
      .finally(() => setClosingToday(false));
  }

  function handleDownloadExcel() {
    if (!report) {
      return;
    }
    downloadBranchDailyCloseExcel(branchId, report.branchName, range.from, range.to).catch(() =>
      setDailyCloseError("Excel indirilemedi."),
    );
  }

  const todayAlreadyFinal = dailyCloseReports.some(
    (row) => row.businessDate === todayIsoDate() && row.status === "FINAL",
  );

  const revenueTrend = [...dailyCloseReports].sort((a, b) => a.businessDate.localeCompare(b.businessDate));

  const topProducts = report
    ? [...report.productBreakdown].sort((a, b) => b.revenueMinorUnits - a.revenueMinorUnits).slice(0, 8)
    : [];
  const topCategories = report
    ? [...report.categoryBreakdown].sort((a, b) => b.revenueMinorUnits - a.revenueMinorUnits)
    : [];

  return (
    <AppShell>
      <main className={adminStyles.page}>
        <PageHeader title="Şube Raporu" description={report ? report.branchName : undefined} />

        <DateRangePresets value={range} onChange={handleRangeChange} />

        {loading ? (
          <TableSkeleton />
        ) : error ? (
          <ErrorState message={error} onRetry={() => loadReport(range)} />
        ) : !report ? null : (
          <>
            <div className={adminStyles.section}>
              <div className={styles.kpiGrid}>
                <KpiCard label="Brüt satış" value={formatPriceMinorUnits(report.grossSalesMinorUnits)} />
                <KpiCard label="Net satış" value={formatPriceMinorUnits(report.netSalesMinorUnits)} />
                <KpiCard
                  label="Refund toplamı"
                  value={formatPriceMinorUnits(report.refundTotalMinorUnits)}
                  tone={report.refundTotalMinorUnits > 0 ? "danger" : "neutral"}
                />
                <KpiCard label="Sipariş sayısı" value={String(report.orderCount)} />
                <KpiCard label="Kabul edilen" value={String(report.acceptedOrderCount)} tone="success" />
                <KpiCard label="Reddedilen" value={String(report.rejectedOrderCount)} tone={report.rejectedOrderCount > 0 ? "danger" : "neutral"} />
                <KpiCard label="Ortalama sepet" value={formatPriceMinorUnits(report.averageOrderValueMinorUnits)} />
                <KpiCard label="Masa ziyareti" value={String(report.tableVisitCount)} />
              </div>
            </div>

            <section className={adminStyles.section}>
              <h2 className={adminStyles.sectionTitle}>Günlük Ciro Trendi (brüt satış)</h2>
              {revenueTrend.length === 0 ? (
                <EmptyState title="Bu aralıkta gün sonu kapanışı yok" description="Trend, gün sonu kapanış kayıtlarından türetilir." />
              ) : (
                <BarList
                  items={revenueTrend.map((row) => ({
                    key: row.businessDate,
                    label: row.businessDate,
                    value: row.grossSalesMinorUnits,
                    valueLabel: formatPriceMinorUnits(row.grossSalesMinorUnits),
                  }))}
                />
              )}
            </section>

            <section className={adminStyles.section}>
              <div className={styles.dailyCloseHeader}>
                <h2 className={adminStyles.sectionTitle}>Gün Sonu Kapanışları</h2>
                <div className={styles.dailyCloseActions}>
                  <Button type="button" variant="secondary" onClick={handleDownloadExcel}>
                    Excel indir
                  </Button>
                  {todayAlreadyFinal ? null : (
                    <Button type="button" onClick={handleCloseToday} disabled={closingToday}>
                      {closingToday ? "Kapatılıyor…" : "Bugünü kapat (FINAL)"}
                    </Button>
                  )}
                </div>
              </div>
              {dailyCloseError ? <ErrorState message={dailyCloseError} /> : null}
              {dailyCloseReports.length === 0 ? (
                <EmptyState title="Bu aralıkta gün sonu kapanışı yok" />
              ) : (
                <Table>
                  <thead>
                    <tr>
                      <th>Tarih</th>
                      <th>Durum</th>
                      <th>Brüt Satış</th>
                      <th>Net Satış</th>
                      <th>Sipariş</th>
                      <th>Bildirim</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dailyCloseReports.map((row) => (
                      <tr key={row.businessDate}>
                        <td className={tableStyles.primary}>{row.businessDate}</td>
                        <td>
                          <Badge tone={row.status === "FINAL" ? "success" : "neutral"}>
                            {row.status === "FINAL" ? "FINAL" : "Ön izleme"}
                          </Badge>
                        </td>
                        <td>{formatPriceMinorUnits(row.grossSalesMinorUnits)}</td>
                        <td>{formatPriceMinorUnits(row.netSalesMinorUnits)}</td>
                        <td>{row.orderCount}</td>
                        <td>
                          {row.status === "FINAL" ? (
                            <NotificationCell
                              logs={notificationsByReport[row.id]}
                              resending={resendingReportId === row.id}
                              onResend={() => handleResend(row.id)}
                            />
                          ) : (
                            "-"
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              )}
            </section>

            {operatingResult ? (
              <section className={adminStyles.section}>
                <h2 className={adminStyles.sectionTitle}>Yönetimsel Net Sonuç</h2>
                <p className={adminStyles.empty} style={{ padding: 0 }}>
                  Vergi, stok maliyeti ve personel tahakkuku gibi kalemler dahil değildir - yasal net kâr değildir.
                </p>
                <div className={styles.kpiGrid}>
                  <KpiCard label="Net satış" value={formatPriceMinorUnits(operatingResult.netSalesMinorUnits)} />
                  <KpiCard label="Onaylı giderler" value={formatPriceMinorUnits(operatingResult.approvedExpensesMinorUnits)} />
                  <KpiCard
                    label="Yönetimsel net sonuç"
                    value={formatPriceMinorUnits(operatingResult.netOperatingResultMinorUnits)}
                    tone={operatingResult.netOperatingResultMinorUnits < 0 ? "danger" : "success"}
                  />
                </div>
              </section>
            ) : null}

            <section className={adminStyles.section}>
              <h2 className={adminStyles.sectionTitle}>Ürün Bazında Satış (ilk 8)</h2>
              {topProducts.length === 0 ? (
                <EmptyState title="Bu aralıkta satış yok" />
              ) : (
                <BarList
                  items={topProducts.map((row) => ({
                    key: row.productId,
                    label: row.productName,
                    value: row.revenueMinorUnits,
                    valueLabel: `${formatPriceMinorUnits(row.revenueMinorUnits)} · ${row.quantitySold} adet`,
                  }))}
                />
              )}
            </section>

            <section className={adminStyles.section}>
              <h2 className={adminStyles.sectionTitle}>Kategori Bazında Ciro</h2>
              {topCategories.length === 0 ? (
                <EmptyState title="Bu aralıkta satış yok" />
              ) : (
                <BarList
                  items={topCategories.map((row) => ({
                    key: row.categoryId,
                    label: row.categoryName,
                    value: row.revenueMinorUnits,
                    valueLabel: formatPriceMinorUnits(row.revenueMinorUnits),
                  }))}
                />
              )}
            </section>

            <section className={adminStyles.section}>
              <h2 className={adminStyles.sectionTitle}>Saatlik Dağılım</h2>
              {report.hourlyDistribution.every((row) => row.orderCount === 0) ? (
                <EmptyState title="Bu aralıkta satış yok" />
              ) : (
                <Table>
                  <thead>
                    <tr>
                      <th>Saat</th>
                      <th>Sipariş</th>
                      <th>Ciro</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.hourlyDistribution
                      .filter((row) => row.orderCount > 0)
                      .map((row) => (
                        <tr key={row.hourOfDay}>
                          <td className={tableStyles.primary}>{String(row.hourOfDay).padStart(2, "0")}:00</td>
                          <td>{row.orderCount}</td>
                          <td>{formatPriceMinorUnits(row.revenueMinorUnits)}</td>
                        </tr>
                      ))}
                  </tbody>
                </Table>
              )}
            </section>
          </>
        )}
      </main>
    </AppShell>
  );
}

function NotificationCell({
  logs,
  resending,
  onResend,
}: {
  logs: OwnerNotificationLog[] | undefined;
  resending: boolean;
  onResend: () => void;
}) {
  if (logs === undefined) {
    return <span className={adminStyles.empty}>…</span>;
  }
  const sent = logs.filter((log) => log.status === "SENT").length;
  const failed = logs.filter((log) => log.status === "FAILED").length;

  return (
    <div className={styles.notificationCell}>
      {logs.length === 0 ? (
        <Badge tone="neutral">Gönderilmedi</Badge>
      ) : (
        <>
          {sent > 0 ? <Badge tone="success">{sent} gönderildi</Badge> : null}
          {failed > 0 ? <Badge tone="danger">{failed} başarısız</Badge> : null}
        </>
      )}
      <Button type="button" variant="secondary" onClick={onResend} disabled={resending}>
        {resending ? "Gönderiliyor…" : "Tekrar Gönder"}
      </Button>
    </div>
  );
}
