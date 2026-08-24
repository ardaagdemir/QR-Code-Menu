"use client";

import { useEffect, useState } from "react";
import { redirect, useParams } from "next/navigation";
import {
  Banknote,
  BarChart3,
  CalendarDays,
  CheckCircle2,
  Clock3,
  Download,
  LockKeyhole,
  Package,
  ReceiptText,
  RotateCcw,
  Scale,
  Shapes,
  ShoppingBag,
  Sparkles,
  Table2,
  TrendingUp,
  Users,
  WalletCards,
  type LucideIcon,
} from "lucide-react";
import {
  ApiError,
  downloadBranchDailyCloseExcel,
  formatPriceMinorUnits,
  generateDailyCloseFinal,
  getBusinessHours,
  getBranchSalesReport,
  getDailyCloseReports,
  getOperatingResult,
  getOwnerNotifications,
  me,
  resendOwnerNotifications,
  type BranchSalesReport,
  type BranchBusinessHoursEntry,
  type DailyCloseReport,
  type HourlySalesRow,
  type OperatingResult,
  type OwnerNotificationLog,
} from "@/lib/api";
import { branchIsoDate } from "@/lib/time";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
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
import { CategoryDonutChart, RevenueAreaChart } from "../ReportCharts";
import styles from "../reports.module.css";

function formatReportDate(isoDate: string): string {
  return new Intl.DateTimeFormat("tr-TR", {
    day: "numeric",
    month: "short",
    year: "numeric",
  })
    .format(new Date(`${isoDate}T12:00:00`))
    .replaceAll(".", "");
}

const DAY_OF_WEEK_BY_INDEX: BranchBusinessHoursEntry["dayOfWeek"][] = [
  "SUNDAY",
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
];

function hourFromTime(time: string | null): number | null {
  if (!time) return null;
  const hour = Number(time.slice(0, 2));
  return Number.isInteger(hour) && hour >= 0 && hour <= 23 ? hour : null;
}

function completeHourlyDistribution(
  rows: HourlySalesRow[],
  businessHours: BranchBusinessHoursEntry[] | null,
): HourlySalesRow[] {
  const todayHours = businessHours?.find((entry) => entry.dayOfWeek === DAY_OF_WEEK_BY_INDEX[new Date().getDay()]);
  const configuredStart = todayHours && !todayHours.closed ? hourFromTime(todayHours.openingTime) : null;
  const configuredEnd = todayHours && !todayHours.closed ? hourFromTime(todayHours.closingTime) : null;
  const startHour = configuredStart ?? 9;
  const endHour = configuredEnd ?? 2;
  const configuredEndOffset = (endHour - startHour + 24) % 24;
  const baseEndOffset = configuredEndOffset === 0 ? 23 : configuredEndOffset;
  const dataEndOffset = rows.reduce((furthest, row) => {
    if (row.orderCount <= 0 && row.revenueMinorUnits <= 0) return furthest;
    return Math.max(furthest, (row.hourOfDay - startHour + 24) % 24);
  }, 0);
  const lastOffset = Math.min(23, Math.max(baseEndOffset, dataEndOffset));
  const rowsByHour = new Map(rows.map((row) => [row.hourOfDay, row]));

  return Array.from({ length: lastOffset + 1 }, (_, offset) => {
    const hourOfDay = (startHour + offset) % 24;
    return rowsByHour.get(hourOfDay) ?? { hourOfDay, orderCount: 0, revenueMinorUnits: 0 };
  });
}

/**
 * Gap-analysis #8 (product-requirements.md Section 13.1) + UI/UX Productization Gate
 * Adım 6 (Bölüm 19.3 "Raporlama"): KPI cards, hızlı tarih presetleri, gelir trendi
 * (gün sonu kapanışlarından türetilen günlük brüt satış bar'ı), ürün/kategori ranking
 * bar'ları, saatlik dağılım ve refund etkisi aynı bilgi hiyerarşisinde. CASHIER/
 * BRANCH_MANAGER/BUSINESS_ADMIN (Permission.REPORT_VIEW) kendi şubeleri için görebilir.
 */
export default function LegacyBranchReportPage() {
  const params = useParams<{ branchId?: string }>();
  if (params.branchId) redirect("/reports");
  return <BranchReportPage />;
}

function BranchReportPage() {
  // Placeholder until `me()` resolves the active branch's real timezone below - branchIsoDate
  // falls back to the device's own date for this brief instant (see lib/time.ts).
  const [range, setRange] = useState<DateRange>(() => presetRange("today"));
  const [branchTimeZone, setBranchTimeZone] = useState<string | null>(null);
  const [report, setReport] = useState<BranchSalesReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [dailyCloseReports, setDailyCloseReports] = useState<DailyCloseReport[]>([]);
  const [closingToday, setClosingToday] = useState(false);
  const [dailyCloseError, setDailyCloseError] = useState<string | null>(null);
  const [notificationsByReport, setNotificationsByReport] = useState<Record<string, OwnerNotificationLog[]>>({});
  const [resendingReportId, setResendingReportId] = useState<string | null>(null);

  const [operatingResult, setOperatingResult] = useState<OperatingResult | null>(null);
  const [businessHours, setBusinessHours] = useState<BranchBusinessHoursEntry[] | null>(null);

  function loadReport(nextRange: DateRange) {
    getBranchSalesReport(nextRange.from, nextRange.to)
      .then((data) => {
        setReport(data);
        setError(null);
      })
      .catch((err) => setError(err instanceof ApiError ? "Rapor yüklenemedi." : "Beklenmedik bir hata oluştu."))
      .finally(() => setLoading(false));
  }

  function reloadDailyClose(nextRange: DateRange) {
    getDailyCloseReports(nextRange.from, nextRange.to)
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
      finalRows.map(async (row) => [row.id, await getOwnerNotifications(row.id).catch(() => [])] as const),
    );
    setNotificationsByReport(Object.fromEntries(entries));
  }

  function handleResend(reportId: string) {
    setResendingReportId(reportId);
    setDailyCloseError(null);
    resendOwnerNotifications(reportId)
      .then((logs) => setNotificationsByReport((prev) => ({ ...prev, [reportId]: logs })))
      .catch(() => setDailyCloseError("Bildirim yeniden gönderilemedi."))
      .finally(() => setResendingReportId(null));
  }

  function reloadOperatingResult(nextRange: DateRange) {
    getOperatingResult(nextRange.from, nextRange.to)
      .then((data) => setOperatingResult(data))
      .catch(() => setOperatingResult(null));
  }

  useEffect(() => {
    // Özet/Kasa/Raporlar must all resolve "today" the same way: from the active branch's
    // own timezone (StaffContext.activeBranchTimeZone), not the device's. Re-anchor the
    // initial "today" preset to it once known, then load everything for that corrected range.
    me()
      .then((context) => {
        setBranchTimeZone(context.activeBranchTimeZone);
        const branchToday = presetRange("today", context.activeBranchTimeZone);
        setRange(branchToday);
        loadReport(branchToday);
        reloadDailyClose(branchToday);
        reloadOperatingResult(branchToday);
      })
      .catch(() => {
        loadReport(range);
        reloadDailyClose(range);
        reloadOperatingResult(range);
      });
    getBusinessHours().then(setBusinessHours).catch(() => setBusinessHours(null));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
    generateDailyCloseFinal(branchIsoDate(branchTimeZone))
      .then(() => reloadDailyClose(range))
      .catch(() => setDailyCloseError("Gün sonu kapatılamadı."))
      .finally(() => setClosingToday(false));
  }

  function handleDownloadExcel() {
    if (!report) {
      return;
    }
    downloadBranchDailyCloseExcel(report.branchName, range.from, range.to).catch(() =>
      setDailyCloseError("Excel indirilemedi."),
    );
  }

  const todayAlreadyFinal = dailyCloseReports.some(
    (row) => row.businessDate === branchIsoDate(branchTimeZone) && row.status === "FINAL",
  );

  const revenueTrend = [...dailyCloseReports].sort((a, b) => a.businessDate.localeCompare(b.businessDate));

  const topProducts = report
    ? [...report.productBreakdown].sort((a, b) => b.revenueMinorUnits - a.revenueMinorUnits).slice(0, 8)
    : [];
  const topCategories = report
    ? [...report.categoryBreakdown].sort((a, b) => b.revenueMinorUnits - a.revenueMinorUnits)
    : [];
  const decidedOrderCount = report
    ? report.acceptedOrderCount + report.rejectedOrderCount
    : 0;
  const acceptanceRate = decidedOrderCount > 0 && report
    ? (report.acceptedOrderCount / decidedOrderCount) * 100
    : 0;
  const acceptanceRateLabel = new Intl.NumberFormat("tr-TR", { maximumFractionDigits: 1 }).format(acceptanceRate);
  const visibleHourlyDistribution = report
    ? completeHourlyDistribution(report.hourlyDistribution, businessHours)
    : [];

  return (
    <AppShell>
      <main className={adminStyles.page}>
        <PageHeader
          title="Raporlar"
          description={report ? `${report.branchName} · satış, sipariş ve operasyon performansı` : "Satış ve operasyon performansını inceleyin."}
        />

        <DateRangePresets value={range} onChange={handleRangeChange} timeZone={branchTimeZone} />

        {loading ? (
          <TableSkeleton />
        ) : error ? (
          <ErrorState message={error} onRetry={() => loadReport(range)} />
        ) : !report ? null : (
          <>
            <section className={styles.summarySection} aria-label="Dönem özeti">
              <ReportSectionHeading icon={Sparkles} title="Dönem Özeti" description="Seçili tarih aralığının temel satış performansı" />
              <div className={styles.kpiGrid}>
                <HeroKpi icon={Banknote} label="Brüt Satış" value={formatPriceMinorUnits(report.grossSalesMinorUnits)} description="İade öncesi toplam satış" />
                <HeroKpi icon={TrendingUp} label="Net Satış" value={formatPriceMinorUnits(report.netSalesMinorUnits)} description="İade sonrası satış" />
                <HeroKpi icon={ShoppingBag} label="Sipariş Sayısı" value={String(report.orderCount)} description="Seçili dönemde alınan sipariş" />
                <HeroKpi icon={ReceiptText} label="Ortalama Sepet" value={formatPriceMinorUnits(report.averageOrderValueMinorUnits)} description="Sipariş başına ortalama" />
              </div>
            </section>

            <section className={`${adminStyles.section} ${adminStyles.panel} ${styles.operationPanel}`}>
              <ReportSectionHeading icon={Scale} title="Operasyon Detayları" description="Sipariş kabulü, ziyaret ve iade metrikleri" />
              <div className={styles.operationGrid}>
                <div className={report.refundTotalMinorUnits > 0 ? `${styles.detailMetric} ${styles.detailMetricAlert}` : styles.detailMetric}>
                  <span className={styles.detailIcon} aria-hidden="true"><RotateCcw size={15} /></span>
                  <span className={styles.detailLabel}>İade Toplamı</span>
                  <strong className={styles.detailValue}>{formatPriceMinorUnits(report.refundTotalMinorUnits)}</strong>
                </div>
                <div className={`${styles.detailMetric} ${styles.acceptanceMetric}`}>
                  <span className={styles.detailIcon} aria-hidden="true"><CheckCircle2 size={15} /></span>
                  <span className={styles.detailLabel}>Kabul Oranı</span>
                  <strong className={styles.detailValue}>{acceptanceRateLabel}%</strong>
                  <span className={styles.detailHint}>{report.acceptedOrderCount} kabul / {report.rejectedOrderCount} red</span>
                  <span className={styles.acceptanceTrack} aria-hidden="true">
                    <span style={{ width: `${Math.min(100, Math.max(0, acceptanceRate))}%` }} />
                  </span>
                </div>
                <div className={styles.detailMetric}>
                  <span className={styles.detailIcon} aria-hidden="true"><Table2 size={15} /></span>
                  <span className={styles.detailLabel}>Masa Ziyareti</span>
                  <strong className={styles.detailValue}>{report.tableVisitCount}</strong>
                </div>
                <div className={styles.detailMetric}>
                  <span className={styles.detailIcon} aria-hidden="true"><Users size={15} /></span>
                  <span className={styles.detailLabel}>Misafir Sayısı</span>
                  <strong className={styles.detailValue}>{report.guestCountRecordedVisitCount > 0 ? report.guestCountTotal : "—"}</strong>
                  <span className={styles.detailHint}>
                    {report.guestCountRecordedVisitCount > 0
                      ? `${report.guestCountRecordedVisitCount} masa ziyaretinde kaydedildi`
                      : "Henüz girilmedi"}
                  </span>
                </div>
              </div>
            </section>

            <div className={styles.chartGrid}>
              <section className={`${adminStyles.section} ${adminStyles.panel} ${styles.chartPanel}`}>
                <ReportSectionHeading icon={BarChart3} title="Günlük Ciro Trendi" description="Gün sonu kapanışlarından brüt satış" />
                {revenueTrend.length === 0 ? (
                  <div className={styles.compactEmpty}>
                    <EmptyState icon={<BarChart3 size={18} />} title="Kapanış verisi yok" description="Trend, gün sonu kayıtlarından oluşur." />
                  </div>
                ) : (
                  <div className={styles.chartBody}>
                    <RevenueAreaChart rows={revenueTrend} />
                  </div>
                )}
              </section>

              <section className={`${adminStyles.section} ${adminStyles.panel} ${styles.chartPanel}`}>
                <ReportSectionHeading icon={Clock3} title="Saatlik Dağılım" description="Sipariş adedi ve saatlik ciro" />
                {report.hourlyDistribution.every((row) => row.orderCount === 0) ? (
                  <div className={styles.compactEmpty}>
                    <EmptyState icon={<Clock3 size={18} />} title="Bu aralıkta satış yok" />
                  </div>
                ) : (
                  <div className={styles.chartBody}>
                    <BarList
                      items={visibleHourlyDistribution.map((row) => ({
                          key: String(row.hourOfDay),
                          label: `${String(row.hourOfDay).padStart(2, "0")}:00`,
                          value: row.revenueMinorUnits,
                          valueLabel: `${row.orderCount} sipariş · ${formatPriceMinorUnits(row.revenueMinorUnits)}`,
                        }))}
                    />
                  </div>
                )}
              </section>
            </div>

            <div className={styles.breakdownGrid}>
              <section className={`${adminStyles.section} ${adminStyles.panel} ${styles.breakdownPanel} ${styles.rankingPanel}`}>
                <ReportSectionHeading icon={Package} title="Ürün Bazında Satış" description="Ciroya göre ilk 8 ürün" />
                {topProducts.length === 0 ? (
                  <div className={styles.compactEmpty}><EmptyState icon={<Package size={18} />} title="Bu aralıkta satış yok" /></div>
                ) : (
                  <BarList
                    items={topProducts.map((row) => ({
                      key: row.productId,
                      label: row.productName,
                      labelTitle: row.productName,
                      value: row.revenueMinorUnits,
                      valueLabel: `${formatPriceMinorUnits(row.revenueMinorUnits)} · ${row.quantitySold} adet`,
                    }))}
                  />
                )}
              </section>

              <section className={`${adminStyles.section} ${adminStyles.panel} ${styles.breakdownPanel}`}>
                <ReportSectionHeading icon={Shapes} title="Kategori Bazında Ciro" description="Kategori gelir dağılımı" />
                {topCategories.length === 0 ? (
                  <div className={styles.compactEmpty}><EmptyState icon={<Shapes size={18} />} title="Bu aralıkta satış yok" /></div>
                ) : (
                  <CategoryDonutChart items={topCategories} />
                )}
              </section>
            </div>

            <section className={`${adminStyles.section} ${adminStyles.panel} ${styles.dailyClosePanel}`}>
              <div className={styles.dailyCloseHeader}>
                <ReportSectionHeading icon={LockKeyhole} title="Gün Sonu Kapanışları" description="Final kayıtları, dışa aktarım ve bildirim durumu" />
                <div className={styles.dailyCloseActions}>
                  <Button type="button" variant="secondary" onClick={handleDownloadExcel}>
                    <Download size={16} aria-hidden="true" /> Excel indir
                  </Button>
                  {todayAlreadyFinal ? null : (
                    <Button type="button" onClick={handleCloseToday} disabled={closingToday}>
                      <LockKeyhole size={16} aria-hidden="true" /> {closingToday ? "Kapatılıyor…" : "Bugünü kapat (Final)"}
                    </Button>
                  )}
                </div>
              </div>
              {dailyCloseError ? <ErrorState message={dailyCloseError} /> : null}
              {dailyCloseReports.length === 0 ? (
                <div className={styles.compactEmpty}><EmptyState icon={<CalendarDays size={18} />} title="Bu aralıkta gün sonu kapanışı yok" /></div>
              ) : (
                <div className={styles.dailyCloseTable}>
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
                          <td className={tableStyles.primary}>{formatReportDate(row.businessDate)}</td>
                          <td>
                            <Badge tone={row.status === "FINAL" ? "success" : "neutral"}>
                              {row.status === "FINAL" ? "Final" : "Ön izleme"}
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
                </div>
              )}
            </section>

            {operatingResult ? (
              <section className={`${adminStyles.section} ${adminStyles.panel} ${styles.financePanel}`}>
                <ReportSectionHeading icon={WalletCards} title="Yönetimsel Net Sonuç" description="Net satış ve gerçekleşmiş giderlerden oluşan finans özeti" />
                <p className={`${adminStyles.empty} ${styles.inlineNote}`}>
                  Vergi, stok maliyeti ve personel tahakkuku gibi kalemler dahil değildir - yasal net kâr değildir.
                </p>
                <div
                  className={`${styles.equationRow} ${operatingResult.netOperatingResultMinorUnits < 0 ? styles.equationRowAlert : ""}`}
                  aria-label="Net satış eksi toplam giderler eşittir yönetimsel net sonuç"
                >
                  <EquationMetric icon={TrendingUp} label="Net Satış" value={formatPriceMinorUnits(operatingResult.netSalesMinorUnits)} />
                  <span className={styles.equationOperator} aria-hidden="true">−</span>
                  <EquationMetric
                    icon={ReceiptText}
                    label="Toplam Giderler"
                    value={formatPriceMinorUnits(operatingResult.totalExpensesMinorUnits)}
                    details={[
                      { label: "Manuel Giderler", value: formatPriceMinorUnits(operatingResult.manualExpensesMinorUnits) },
                      { label: "Tekrarlayan Giderler", value: formatPriceMinorUnits(operatingResult.recurringExpensesMinorUnits) },
                    ]}
                  />
                  <span className={styles.equationOperator} aria-hidden="true">=</span>
                  <EquationMetric icon={Scale} label="Yönetimsel Net Sonuç" value={formatPriceMinorUnits(operatingResult.netOperatingResultMinorUnits)} result />
                </div>
              </section>
            ) : null}
          </>
        )}
      </main>
    </AppShell>
  );
}

function ReportSectionHeading({ icon: Icon, title, description }: { icon: LucideIcon; title: string; description: string }) {
  return (
    <div className={styles.sectionIntro}>
      <span className={styles.sectionIcon} aria-hidden="true"><Icon size={17} /></span>
      <div>
        <h2 className={adminStyles.sectionTitle}>{title}</h2>
        <p className={styles.sectionDescription}>{description}</p>
      </div>
    </div>
  );
}

function HeroKpi({ icon: Icon, label, value, description }: { icon: LucideIcon; label: string; value: string; description: string }) {
  return (
    <div className={styles.heroKpi}>
      <span className={styles.heroIcon} aria-hidden="true"><Icon size={21} /></span>
      <span className={styles.heroLabel}>{label}</span>
      <strong className={styles.heroValue}>{value}</strong>
      <span className={styles.heroDescription}>{description}</span>
    </div>
  );
}

function EquationMetric({
  icon: Icon,
  label,
  value,
  details,
  result = false,
}: {
  icon: LucideIcon;
  label: string;
  value: string;
  details?: { label: string; value: string }[];
  result?: boolean;
}) {
  return (
    <div className={`${styles.equationMetric} ${details ? styles.equationMetricWithDetails : ""} ${result ? styles.equationResult : ""}`}>
      <span className={styles.equationIcon} aria-hidden="true"><Icon size={18} /></span>
      <span className={styles.equationLabel}>{label}</span>
      <strong className={styles.equationValue}>{value}</strong>
      {details ? (
        <dl className={styles.expenseBreakdown}>
          {details.map((detail) => (
            <div key={detail.label}>
              <dt>{detail.label}</dt>
              <dd>{detail.value}</dd>
            </div>
          ))}
        </dl>
      ) : null}
    </div>
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
        {resending ? "Gönderiliyor…" : "Tekrar gönder"}
      </Button>
    </div>
  );
}
