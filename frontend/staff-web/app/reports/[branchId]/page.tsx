"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { redirect, useParams } from "next/navigation";
import {
  Bell,
  CalendarDays,
  ContactRound,
  Download,
  LockKeyhole,
  Package,
  Pencil,
  Plus,
  ReceiptText,
  Banknote,
  RotateCcw,
  Scale,
  Shapes,
  ShoppingBag,
  Clock3,
  Table2,
  Trash2,
  TrendingUp,
  Users,
  WalletCards,
  type LucideIcon,
} from "lucide-react";
import {
  ApiError,
  createBusinessContact,
  deleteBusinessContact,
  downloadBranchDailyCloseExcel,
  formatPriceMinorUnits,
  generateDailyCloseFinal,
  getBusinessHours,
  getBranchSalesReport,
  getDailyCloseReports,
  getOperatingResult,
  getOwnerNotifications,
  getReportNotifications,
  listBusinessContacts,
  me,
  resendMonthlyReportNotifications,
  resendOwnerNotifications,
  updateBusinessContact,
  type BranchSalesReport,
  type BranchBusinessHoursEntry,
  type BusinessContact,
  type BusinessContactInput,
  type DailyCloseReport,
  type HourlySalesRow,
  type OperatingResult,
  type OwnerNotificationLog,
  type ReportNotificationLog,
  type StaffContext,
} from "@/lib/api";
import { branchIsoDate } from "@/lib/time";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import { presetRange } from "@/components/ui/DateRangePresets";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import { useToast } from "@/components/ui/ToastProvider";
import tableStyles from "@/components/ui/Table.module.css";
import adminStyles from "@/styles/admin.module.css";
import BarList from "@/components/ui/BarList";
import ContactFormDialog from "../ContactFormDialog";
import { CategoryDonutChart, HourlyBarChart } from "../ReportCharts";
import styles from "../reports.module.css";

const EMPTY_CONTACT_INPUT: BusinessContactInput = {
  name: "",
  phone: "",
  email: "",
  whatsappEnabled: false,
  dailyReportRecipient: false,
  monthlyReportRecipient: false,
};

type ContactFormState =
  | { mode: "create"; value: BusinessContactInput }
  | { mode: "edit"; contactId: string; value: BusinessContactInput };

function formatReportDate(isoDate: string): string {
  return new Intl.DateTimeFormat("tr-TR", {
    day: "numeric",
    month: "short",
    year: "numeric",
  })
    .format(new Date(`${isoDate}T12:00:00`))
    .replaceAll(".", "");
}

/** "Rapor Bildirimleri" tablosunda MONTHLY satırların Dönem kolonu için: "2026-07-01" -> "Temmuz 2026". */
function formatMonthPeriod(isoDate: string): string {
  const formatted = new Intl.DateTimeFormat("tr-TR", { month: "long", year: "numeric" }).format(
    new Date(`${isoDate}T12:00:00`),
  );
  return formatted.charAt(0).toUpperCase() + formatted.slice(1);
}

/** Aylık Finansal Özet'in başlığındaki dönem rozeti: ay henüz bitmediyse "1–26 Ağustos 2026"
 * gibi kısmi aralık, ayın son günündeyse (monthRange.to == ayın son günü) tam ay adı. */
function formatMonthRangeLabel(range: { from: string; to: string }): string {
  const fromDate = new Date(`${range.from}T12:00:00`);
  const toDate = new Date(`${range.to}T12:00:00`);
  const lastDayOfMonth = new Date(fromDate.getFullYear(), fromDate.getMonth() + 1, 0).getDate();
  const monthLabel = new Intl.DateTimeFormat("tr-TR", { month: "long", year: "numeric" }).format(fromDate);
  const capitalizedMonthLabel = monthLabel.charAt(0).toUpperCase() + monthLabel.slice(1);
  return toDate.getDate() >= lastDayOfMonth ? capitalizedMonthLabel : `1–${toDate.getDate()} ${capitalizedMonthLabel}`;
}

function formatAttemptedAt(isoInstant: string): string {
  return new Intl.DateTimeFormat("tr-TR", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  })
    .format(new Date(isoInstant))
    .replaceAll(".", "");
}

type AnalysisPeriod = "day" | "week" | "month";

const PERIOD_LABELS: Record<AnalysisPeriod, string> = {
  day: "Günlük",
  week: "Haftalık",
  month: "Aylık",
};

function periodDateRange(period: AnalysisPeriod, timeZone: string | null) {
  if (period === "day") return presetRange("today", timeZone);
  if (period === "week") return presetRange("week", timeZone);
  return presetRange("month", timeZone);
}

/** Saatlik Satış Dağılımı/Ürün Bazında Satış/Kategori Bazında Ciro'nun her biri bağımsız
 * Günlük/Haftalık/Aylık dönem seçebilir - üçü de aynı `getBranchSalesReport` uç noktasını
 * kendi aralığıyla çağırır (gerçek reporting verisi, sentetik/heuristic veri yok). */
function useAnalysisPeriod() {
  const [period, setPeriod] = useState<AnalysisPeriod>("day");
  const [data, setData] = useState<BranchSalesReport | null>(null);
  const [loading, setLoading] = useState(false);
  const requestRef = useRef(0);

  function reload(nextPeriod: AnalysisPeriod, timeZone: string | null) {
    setPeriod(nextPeriod);
    const requestId = ++requestRef.current;
    setLoading(true);
    const range = periodDateRange(nextPeriod, timeZone);
    getBranchSalesReport(range.from, range.to)
      .then((result) => {
        if (requestRef.current === requestId) setData(result);
      })
      .catch(() => {})
      .finally(() => {
        if (requestRef.current === requestId) setLoading(false);
      });
  }

  return { period, data, setData, loading, reload };
}

/** Rapor Alıcıları/Rapor Bildirimleri kayıt arttıkça kart yüksekliği sonsuza büyümesin diye
 * sayfa başına en fazla 5 kayıt gösterilir, gerisi pagination'a gider. */
const LIST_PAGE_SIZE = 5;

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
 * Adım 6 (Bölüm 19.3 "Raporlama"): docs/design/raporlar-ekrani.png referans yerleşimi -
 * bugüne sabit 5 KPI kartı, 3 grafik (saatlik dağılım/ürün/kategori), Gün Sonu
 * Kapanışları + Aylık Finansal Özet yan yana, Rapor Alıcıları + Rapor Bildirimleri yan
 * yana. CASHIER/BRANCH_MANAGER/BUSINESS_ADMIN (Permission.REPORT_VIEW) kendi şubeleri
 * için görebilir; Rapor Alıcıları yönetimi yalnız BUSINESS_ADMIN'e açık.
 */
export default function LegacyBranchReportPage() {
  const params = useParams<{ branchId?: string }>();
  if (params.branchId) redirect("/reports");
  return <BranchReportPage />;
}

function BranchReportPage() {
  const { showToast } = useToast();

  const [branchTimeZone, setBranchTimeZone] = useState<string | null>(null);
  const [staffContext, setStaffContext] = useState<StaffContext | null>(null);
  const [report, setReport] = useState<BranchSalesReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Gün Sonu Kapanışları + Aylık Finansal Özet her ikisi de "içinde bulunulan ay" kapsamında -
  // referans görselde bir arada gösterildikleri için aynı sabit aralığı paylaşırlar. Artık bir
  // tarih aralığı seçici yok (bkz. docs/design/raporlar-ekrani.png: üstteki KPI'lar/grafikler
  // hep "Bugün", alttaki finansal özet hep "bu ay").
  const [monthRange, setMonthRange] = useState(() => presetRange("month"));
  const [dailyCloseReports, setDailyCloseReports] = useState<DailyCloseReport[]>([]);
  const [closingToday, setClosingToday] = useState(false);
  const [dailyCloseError, setDailyCloseError] = useState<string | null>(null);
  const [notificationsByReport, setNotificationsByReport] = useState<Record<string, OwnerNotificationLog[]>>({});
  const [resendingReportId, setResendingReportId] = useState<string | null>(null);

  const [operatingResult, setOperatingResult] = useState<OperatingResult | null>(null);
  const [businessHours, setBusinessHours] = useState<BranchBusinessHoursEntry[] | null>(null);

  const hourly = useAnalysisPeriod();
  const products = useAnalysisPeriod();
  const categories = useAnalysisPeriod();

  // Rapor Alıcıları (Section 12.3 BusinessContact) - İşletme Ayarları'ndan taşındı, çünkü
  // BUSINESS_SETTINGS_MANAGE'e sahip olmayan REPORT_VIEW rolleri (BRANCH_MANAGER/CASHIER)
  // Raporlar'ı görebiliyor ama İşletme Ayarları'na hiç erişemiyordu.
  const [contacts, setContacts] = useState<BusinessContact[]>([]);
  const [loadingContacts, setLoadingContacts] = useState(true);
  const [contactsError, setContactsError] = useState<string | null>(null);
  const [contactForm, setContactForm] = useState<ContactFormState | null>(null);
  const [savingContact, setSavingContact] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<BusinessContact | null>(null);
  const [deletingContactId, setDeletingContactId] = useState<string | null>(null);
  const [contactsPage, setContactsPage] = useState(0);

  // Rapor Bildirimleri: DAILY+MONTHLY birleşik gönderim geçmişi.
  const [reportNotifications, setReportNotifications] = useState<ReportNotificationLog[]>([]);
  const [loadingReportNotifications, setLoadingReportNotifications] = useState(true);
  const [reportNotificationsError, setReportNotificationsError] = useState<string | null>(null);
  const [resendingNotificationKey, setResendingNotificationKey] = useState<string | null>(null);
  const [notificationsPage, setNotificationsPage] = useState(0);

  function loadReport(todayRange: { from: string; to: string }) {
    getBranchSalesReport(todayRange.from, todayRange.to)
      .then((data) => {
        setReport(data);
        // Analiz kartlarının varsayılan dönemi "Günlük" = bugün, yani ilk yüklemede aynı
        // veriyi 3 kez daha fetch etmek yerine tek çağrının sonucu paylaşılır.
        hourly.setData(data);
        products.setData(data);
        categories.setData(data);
        setError(null);
      })
      .catch((err) => setError(err instanceof ApiError ? "Rapor yüklenemedi." : "Beklenmedik bir hata oluştu."))
      .finally(() => setLoading(false));
  }

  function reloadDailyClose(nextRange: { from: string; to: string }) {
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

  function openCreateContact() {
    setContactForm({ mode: "create", value: EMPTY_CONTACT_INPUT });
  }

  function openEditContact(contact: BusinessContact) {
    setContactForm({
      mode: "edit",
      contactId: contact.id,
      value: {
        name: contact.name,
        phone: contact.phone ?? "",
        email: contact.email ?? "",
        whatsappEnabled: contact.whatsappEnabled,
        dailyReportRecipient: contact.dailyReportRecipient,
        monthlyReportRecipient: contact.monthlyReportRecipient,
      },
    });
  }

  async function handleSubmitContact(event: React.FormEvent) {
    event.preventDefault();
    if (!contactForm || !contactForm.value.name.trim()) {
      return;
    }
    setSavingContact(true);
    try {
      const value = { ...contactForm.value, name: contactForm.value.name.trim() };
      if (contactForm.mode === "create") {
        await createBusinessContact(value);
        showToast("Alıcı eklendi.", "success");
      } else {
        await updateBusinessContact(contactForm.contactId, value);
        showToast("Alıcı güncellendi.", "success");
      }
      setContactForm(null);
      loadContacts();
    } catch {
      showToast(contactForm.mode === "create" ? "Alıcı oluşturulamadı." : "Alıcı güncellenemedi.", "error");
    } finally {
      setSavingContact(false);
    }
  }

  async function handleConfirmDeleteContact() {
    if (!deleteTarget) {
      return;
    }
    setDeletingContactId(deleteTarget.id);
    try {
      await deleteBusinessContact(deleteTarget.id);
      setDeleteTarget(null);
      loadContacts();
      showToast("Alıcı silindi.", "success");
    } catch {
      showToast("Alıcı silinemedi.", "error");
    } finally {
      setDeletingContactId(null);
    }
  }

  /** DAILY satırları mevcut per-report resend endpoint'ini, MONTHLY satırları yeni manuel
   * resend endpoint'ini çağırır - ikisi de aynı (report/period) için tüm alıcılara toplu
   * gönderim yapar, tek bir satırın alıcısına değil (Gün Sonu Kapanışları'ndaki mevcut
   * "Tekrar Gönder" davranışıyla simetrik). */
  function handleReportNotificationResend(entry: ReportNotificationLog) {
    if (entry.reportType === "DAILY" && !entry.dailyCloseReportId) {
      return;
    }
    if (entry.reportType === "MONTHLY" && !entry.period) {
      return;
    }
    const key = notificationKey(entry);
    setResendingNotificationKey(key);
    setReportNotificationsError(null);
    const request =
      entry.reportType === "DAILY"
        ? resendOwnerNotifications(entry.dailyCloseReportId as string)
        : resendMonthlyReportNotifications((entry.period as string).slice(0, 7));
    request
      .then(() => {
        loadReportNotifications();
        if (entry.reportType === "DAILY" && entry.dailyCloseReportId) {
          const reportId = entry.dailyCloseReportId;
          getOwnerNotifications(reportId)
            .then((logs) => setNotificationsByReport((prev) => ({ ...prev, [reportId]: logs })))
            .catch(() => {});
        }
      })
      .catch(() => setReportNotificationsError("Bildirim yeniden gönderilemedi."))
      .finally(() => setResendingNotificationKey(null));
  }

  function notificationKey(entry: ReportNotificationLog): string {
    return entry.reportType === "DAILY" ? `DAILY:${entry.dailyCloseReportId}` : `MONTHLY:${entry.period}`;
  }

  function reloadOperatingResult(nextRange: { from: string; to: string }) {
    getOperatingResult(nextRange.from, nextRange.to)
      .then((data) => setOperatingResult(data))
      .catch(() => setOperatingResult(null));
  }

  function loadContacts() {
    listBusinessContacts()
      .then((data) => {
        setContacts(data);
        setContactsError(null);
      })
      .catch(() => setContactsError("Alıcılar yüklenemedi."))
      .finally(() => setLoadingContacts(false));
  }

  function loadReportNotifications() {
    setLoadingReportNotifications(true);
    getReportNotifications()
      .then((data) => {
        setReportNotifications(data);
        setReportNotificationsError(null);
      })
      .catch(() => setReportNotificationsError("Bildirimler yüklenemedi."))
      .finally(() => setLoadingReportNotifications(false));
  }

  useEffect(() => {
    // Özet/Kasa/Raporlar must all resolve "today" the same way: from the active branch's
    // own timezone (StaffContext.activeBranchTimeZone), not the device's.
    me()
      .then((context) => {
        setStaffContext(context);
        setBranchTimeZone(context.activeBranchTimeZone);
        const todayRange = presetRange("today", context.activeBranchTimeZone);
        const currentMonthRange = presetRange("month", context.activeBranchTimeZone);
        setMonthRange(currentMonthRange);
        loadReport(todayRange);
        reloadDailyClose(currentMonthRange);
        reloadOperatingResult(currentMonthRange);
      })
      .catch(() => {
        const todayRange = presetRange("today");
        loadReport(todayRange);
        reloadDailyClose(monthRange);
        reloadOperatingResult(monthRange);
      });
    getBusinessHours().then(setBusinessHours).catch(() => setBusinessHours(null));
    loadContacts();
    loadReportNotifications();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleCloseToday() {
    setClosingToday(true);
    setDailyCloseError(null);
    generateDailyCloseFinal(branchIsoDate(branchTimeZone))
      .then(() => reloadDailyClose(monthRange))
      .catch(() => setDailyCloseError("Gün sonu kapatılamadı."))
      .finally(() => setClosingToday(false));
  }

  function handleDownloadExcel() {
    if (!report) {
      return;
    }
    downloadBranchDailyCloseExcel(report.branchName, monthRange.from, monthRange.to).catch(() =>
      setDailyCloseError("Excel indirilemedi."),
    );
  }

  const isBusinessAdmin = staffContext?.role === "BUSINESS_ADMIN";

  const todayAlreadyFinal = dailyCloseReports.some(
    (row) => row.businessDate === branchIsoDate(branchTimeZone) && row.status === "FINAL",
  );

  const topProducts = products.data
    ? [...products.data.productBreakdown].sort((a, b) => b.revenueMinorUnits - a.revenueMinorUnits).slice(0, 8)
    : [];
  const topCategories = categories.data
    ? [...categories.data.categoryBreakdown].sort((a, b) => b.revenueMinorUnits - a.revenueMinorUnits)
    : [];
  const visibleHourlyDistribution = hourly.data
    ? completeHourlyDistribution(hourly.data.hourlyDistribution, businessHours)
    : [];

  const contactsPageCount = Math.max(1, Math.ceil(contacts.length / LIST_PAGE_SIZE));
  const contactsPageIndex = Math.min(contactsPage, contactsPageCount - 1);
  const pagedContacts = contacts.slice(
    contactsPageIndex * LIST_PAGE_SIZE,
    contactsPageIndex * LIST_PAGE_SIZE + LIST_PAGE_SIZE,
  );

  const notificationsPageCount = Math.max(1, Math.ceil(reportNotifications.length / LIST_PAGE_SIZE));
  const notificationsPageIndex = Math.min(notificationsPage, notificationsPageCount - 1);
  const pagedReportNotifications = reportNotifications.slice(
    notificationsPageIndex * LIST_PAGE_SIZE,
    notificationsPageIndex * LIST_PAGE_SIZE + LIST_PAGE_SIZE,
  );

  return (
    <AppShell>
      <main className={`${adminStyles.page} ${styles.reportsPageWide}`}>
        <PageHeader
          title="Raporlar"
          description={
            report
              ? `${report.branchName} · şubenizin günlük performansını ve aylık finansal özetini izleyin.`
              : "Şubenizin günlük performansını ve aylık finansal özetini izleyin."
          }
        />

        {loading ? (
          <TableSkeleton />
        ) : error ? (
          <ErrorState message={error} onRetry={() => loadReport(presetRange("today", branchTimeZone))} />
        ) : !report ? null : (
          <>
            <div className={styles.kpiGrid}>
              <HeroKpi icon={TrendingUp} label="Günlük Satış Tutarı" value={formatPriceMinorUnits(report.netSalesMinorUnits)} badge="Bugün" />
              <HeroKpi icon={ShoppingBag} label="Günlük Sipariş Sayısı" value={String(report.orderCount)} badge="Bugün" />
              <HeroKpi icon={RotateCcw} label="İade Sayısı" value={String(report.refundCount)} badge="Bugün" />
              <HeroKpi icon={Banknote} label="İade Tutarı" value={formatPriceMinorUnits(report.refundTotalMinorUnits)} badge="Bugün" />
              <HeroKpi icon={Table2} label="Masa Ziyareti" value={String(report.tableVisitCount)} badge="Bugün" />
              <HeroKpi icon={Users} label="Müşteri Sayısı" value={String(report.guestCountTotal)} badge="Bugün" />
            </div>

            <div className={styles.chartTrio}>
              <section className={`${adminStyles.section} ${adminStyles.panel} ${styles.chartPanel}`}>
                <ReportSectionHeading
                  icon={Clock3}
                  title="Saatlik Satış Dağılımı"
                  description="Sipariş adedi ve saatlik ciro"
                  right={
                    <PeriodToggle
                      value={hourly.period}
                      loading={hourly.loading}
                      onChange={(period) => hourly.reload(period, branchTimeZone)}
                    />
                  }
                />
                {!hourly.data || hourly.data.hourlyDistribution.every((row) => row.orderCount === 0) ? (
                  <div className={styles.compactEmpty}>
                    <EmptyState icon={<Clock3 size={18} />} title="Bu aralıkta satış yok" />
                  </div>
                ) : (
                  <div className={`${styles.chartBody} ${hourly.loading ? styles.panelLoading : ""}`}>
                    <HourlyBarChart rows={visibleHourlyDistribution} />
                  </div>
                )}
              </section>

              <section className={`${adminStyles.section} ${adminStyles.panel} ${styles.breakdownPanel} ${styles.rankingPanel}`}>
                <ReportSectionHeading
                  icon={Package}
                  title="Ürün Bazında Satış"
                  description="Ciroya göre ilk 8 ürün"
                  right={
                    <PeriodToggle
                      value={products.period}
                      loading={products.loading}
                      onChange={(period) => products.reload(period, branchTimeZone)}
                    />
                  }
                />
                {topProducts.length === 0 ? (
                  <div className={styles.compactEmpty}><EmptyState icon={<Package size={18} />} title="Bu aralıkta satış yok" /></div>
                ) : (
                  <div className={products.loading ? styles.panelLoading : undefined}>
                    <BarList
                      items={topProducts.map((row) => ({
                        key: row.productId,
                        label: row.productName,
                        labelTitle: row.productName,
                        value: row.revenueMinorUnits,
                        valueLabel: formatPriceMinorUnits(row.revenueMinorUnits),
                      }))}
                    />
                  </div>
                )}
              </section>

              <section className={`${adminStyles.section} ${adminStyles.panel} ${styles.breakdownPanel}`}>
                <ReportSectionHeading
                  icon={Shapes}
                  title="Kategori Bazında Ciro"
                  description="Kategori gelir dağılımı"
                  right={
                    <PeriodToggle
                      value={categories.period}
                      loading={categories.loading}
                      onChange={(period) => categories.reload(period, branchTimeZone)}
                    />
                  }
                />
                {topCategories.length === 0 ? (
                  <div className={styles.compactEmpty}><EmptyState icon={<Shapes size={18} />} title="Bu aralıkta satış yok" /></div>
                ) : (
                  <div className={categories.loading ? styles.panelLoading : undefined}>
                    <CategoryDonutChart items={topCategories} />
                  </div>
                )}
              </section>
            </div>

            <div className={styles.pairGrid}>
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
                  <ReportSectionHeading
                    icon={WalletCards}
                    title="Aylık Finansal Özet"
                    description="Bu ayın satış ve gerçekleşmiş giderlerine göre hesaplanır."
                    note="Yasal net kâr değildir."
                    right={
                      <Badge tone="neutral">
                        <span className={styles.periodBadgeText}>{formatMonthRangeLabel(monthRange)}</span>
                      </Badge>
                    }
                  />
                  <div
                    className={`${styles.equationRow} ${operatingResult.netOperatingResultMinorUnits < 0 ? styles.equationRowAlert : ""}`}
                    aria-label="Toplam aylık satış eksi toplam giderler eşittir yönetimsel net sonuç"
                  >
                    <EquationMetric icon={TrendingUp} label="Toplam Aylık Satış" value={formatPriceMinorUnits(operatingResult.netSalesMinorUnits)} />
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
            </div>

            <div className={styles.pairGrid}>
              {isBusinessAdmin ? (
                <section className={`${adminStyles.section} ${adminStyles.panel} ${styles.contactsPanel}`}>
                  <PageHeader
                    title="Rapor Alıcıları"
                    description="Raporların otomatik gönderileceği alıcıları yönetin."
                    actions={
                      <Button onClick={openCreateContact}>
                        <Plus size={16} aria-hidden="true" /> Alıcı Ekle
                      </Button>
                    }
                  />

                  {loadingContacts ? (
                    <TableSkeleton />
                  ) : contactsError ? (
                    <ErrorState message={contactsError} onRetry={loadContacts} />
                  ) : contacts.length === 0 ? (
                    <div className={styles.compactEmpty}>
                      <EmptyState icon={<ContactRound size={20} />} title="Henüz alıcı yok" description="Rapor alacak ilk alıcıyı ekleyin." />
                    </div>
                  ) : (
                    <Table>
                      <thead>
                        <tr>
                          <th>Ad Soyad</th>
                          <th>E-posta</th>
                          <th>Rapor Tercihleri</th>
                          <th className={styles.actionsHeader} aria-label="İşlemler"></th>
                        </tr>
                      </thead>
                      <tbody>
                        {pagedContacts.map((contact) => (
                          <tr key={contact.id}>
                            <td className={tableStyles.primary}>{contact.name}</td>
                            <td className={tableStyles.muted}>{contact.email || "İletişim bilgisi yok"}</td>
                            <td>
                              <div className={styles.reportPreferences}>
                                {contact.dailyReportRecipient ? <Badge tone="info">Günlük rapor</Badge> : null}
                                {contact.monthlyReportRecipient ? <Badge tone="neutral">Aylık rapor</Badge> : null}
                                {!contact.dailyReportRecipient && !contact.monthlyReportRecipient ? (
                                  <span className={styles.noPreference}>Rapor tercihi yok</span>
                                ) : null}
                              </div>
                            </td>
                            <td>
                              <div className={`${tableStyles.actions} ${styles.contactActions}`}>
                                <Button size="sm" variant="secondary" onClick={() => openEditContact(contact)}>
                                  <Pencil size={14} aria-hidden="true" /> Düzenle
                                </Button>
                                <Button
                                  size="sm"
                                  variant="danger"
                                  disabled={deletingContactId === contact.id}
                                  onClick={() => setDeleteTarget(contact)}
                                >
                                  <Trash2 size={14} aria-hidden="true" /> Sil
                                </Button>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </Table>
                  )}
                  {contacts.length > LIST_PAGE_SIZE ? (
                    <Pager
                      page={contactsPageIndex + 1}
                      pageCount={contactsPageCount}
                      onPrev={() => setContactsPage((page) => Math.max(0, page - 1))}
                      onNext={() => setContactsPage((page) => Math.min(contactsPageCount - 1, page + 1))}
                    />
                  ) : null}
                </section>
              ) : null}

              <section
                className={`${adminStyles.section} ${adminStyles.panel} ${styles.notificationsPanel}`}
                style={isBusinessAdmin ? undefined : { gridColumn: "1 / -1" }}
              >
                <ReportSectionHeading icon={Bell} title="Rapor Bildirimleri" description="Günlük ve aylık rapor gönderim geçmişi" />
                {reportNotificationsError ? <ErrorState message={reportNotificationsError} /> : null}
                {loadingReportNotifications ? (
                  <TableSkeleton />
                ) : reportNotifications.length === 0 ? (
                  <div className={styles.compactEmpty}>
                    <EmptyState icon={<Bell size={18} />} title="Henüz rapor bildirimi yok" />
                  </div>
                ) : (
                  <div className={styles.notificationsTable}>
                    <Table>
                      <thead>
                        <tr>
                          <th>Rapor Türü</th>
                          <th>Dönem</th>
                          <th>Alıcı</th>
                          <th>Gönderim Tarihi</th>
                          <th>Durum</th>
                          <th className={styles.actionsHeader} aria-label="İşlem"></th>
                        </tr>
                      </thead>
                      <tbody>
                        {pagedReportNotifications.map((entry) => {
                          const key = notificationKey(entry);
                          const resending = resendingNotificationKey === key;
                          return (
                            <tr key={entry.id}>
                              <td className={tableStyles.primary}>
                                {entry.reportType === "DAILY" ? "Günlük Rapor" : "Aylık Rapor"}
                              </td>
                              <td>
                                {entry.period
                                  ? entry.reportType === "DAILY"
                                    ? formatReportDate(entry.period)
                                    : formatMonthPeriod(entry.period)
                                  : "-"}
                              </td>
                              <td className={`${tableStyles.muted} ${styles.notificationEmailCell}`} title={entry.recipientEmail}>
                                {entry.recipientEmail}
                              </td>
                              <td>{formatAttemptedAt(entry.attemptedAt)}</td>
                              <td>
                                <Badge tone={entry.status === "SENT" ? "success" : "danger"}>
                                  {entry.status === "SENT" ? "Gönderildi" : "Başarısız"}
                                </Badge>
                              </td>
                              <td className={styles.actionsHeader}>
                                <Button
                                  size="sm"
                                  variant="secondary"
                                  disabled={resending}
                                  className={styles.notificationsTableAction}
                                  onClick={() => handleReportNotificationResend(entry)}
                                >
                                  {resending ? "Gönderiliyor…" : entry.status === "FAILED" ? "Yeniden Dene" : "Tekrar Gönder"}
                                </Button>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </Table>
                  </div>
                )}
                {reportNotifications.length > LIST_PAGE_SIZE ? (
                  <Pager
                    page={notificationsPageIndex + 1}
                    pageCount={notificationsPageCount}
                    onPrev={() => setNotificationsPage((page) => Math.max(0, page - 1))}
                    onNext={() => setNotificationsPage((page) => Math.min(notificationsPageCount - 1, page + 1))}
                  />
                ) : null}
              </section>
            </div>
          </>
        )}
      </main>

      {contactForm ? (
        <ContactFormDialog
          mode={contactForm.mode}
          value={contactForm.value}
          onChange={(value) => setContactForm((current) => (current ? { ...current, value } : current))}
          onSubmit={handleSubmitContact}
          onClose={() => setContactForm(null)}
          submitting={savingContact}
        />
      ) : null}

      {deleteTarget ? (
        <ConfirmDialog
          title="Alıcıyı sil"
          message={`"${deleteTarget.name}" adlı alıcıyı silmek istediğinize emin misiniz? Bu işlem geri alınamaz.`}
          confirmLabel="Sil"
          tone="danger"
          confirmLoading={deletingContactId === deleteTarget.id}
          onConfirm={handleConfirmDeleteContact}
          onCancel={() => setDeleteTarget(null)}
        />
      ) : null}
    </AppShell>
  );
}

function ReportSectionHeading({
  icon: Icon,
  title,
  description,
  note,
  badge,
  right,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
  note?: string;
  badge?: string;
  right?: ReactNode;
}) {
  return (
    <div className={styles.sectionIntro}>
      <div className={styles.sectionIntroMain}>
        <span className={styles.sectionIcon} aria-hidden="true"><Icon size={17} /></span>
        <div>
          <h2 className={adminStyles.sectionTitle}>{title}</h2>
          <p className={styles.sectionDescription}>{description}</p>
          {note ? <p className={styles.sectionNote}>{note}</p> : null}
        </div>
      </div>
      {right ?? (badge ? <Badge tone="neutral">{badge}</Badge> : null)}
    </div>
  );
}

function PeriodToggle({
  value,
  onChange,
  loading,
}: {
  value: AnalysisPeriod;
  onChange: (period: AnalysisPeriod) => void;
  loading?: boolean;
}) {
  return (
    <div className={styles.periodToggle} role="group" aria-label="Dönem seçimi">
      {(Object.keys(PERIOD_LABELS) as AnalysisPeriod[]).map((period) => (
        <button
          key={period}
          type="button"
          className={`${styles.periodToggleButton} ${value === period ? styles.periodToggleButtonActive : ""}`}
          aria-pressed={value === period}
          disabled={loading}
          onClick={() => onChange(period)}
        >
          {PERIOD_LABELS[period]}
        </button>
      ))}
    </div>
  );
}

function Pager({
  page,
  pageCount,
  onPrev,
  onNext,
}: {
  page: number;
  pageCount: number;
  onPrev: () => void;
  onNext: () => void;
}) {
  return (
    <div className={styles.pager}>
      <Button type="button" variant="secondary" size="sm" onClick={onPrev} disabled={page <= 1}>
        Önceki
      </Button>
      <span className={styles.pagerLabel}>
        Sayfa {page} / {pageCount}
      </span>
      <Button type="button" variant="secondary" size="sm" onClick={onNext} disabled={page >= pageCount}>
        Sonraki
      </Button>
    </div>
  );
}

function HeroKpi({ icon: Icon, label, value, badge }: { icon: LucideIcon; label: string; value: string; badge: string }) {
  return (
    <div className={styles.heroKpi}>
      <span className={styles.heroIcon} aria-hidden="true"><Icon size={21} /></span>
      <div className={styles.heroTopRow}>
        <span className={styles.heroLabel}>{label}</span>
        <Badge tone="neutral">{badge}</Badge>
      </div>
      <strong className={styles.heroValue}>{value}</strong>
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
  const failedLogs = logs.filter((log) => log.status === "FAILED");
  const failed = failedLogs.length;
  const alreadySent = logs.length > 0;
  const failedTitle = failedLogs
    .map((log) => `${log.recipientEmail}: ${log.errorMessage ?? "bilinmeyen hata"}`)
    .join("\n");

  return (
    <div className={styles.notificationCell}>
      {alreadySent ? (
        <>
          {sent > 0 ? <Badge tone="success">{sent} gönderildi</Badge> : null}
          {failed > 0 ? (
            <Badge tone="danger" title={failedTitle}>
              {failed} başarısız
            </Badge>
          ) : null}
        </>
      ) : (
        <Badge tone="neutral">Gönderilmedi</Badge>
      )}
      <Button
        type="button"
        variant="secondary"
        className={styles.notificationResendButton}
        onClick={onResend}
        disabled={resending}
      >
        {resending ? (
          <RotateCcw size={13} className={styles.notificationSpinner} aria-hidden="true" />
        ) : null}
        {resending ? "Gönderiliyor…" : alreadySent ? "Tekrar Gönder" : "Gönder"}
      </Button>
    </div>
  );
}
