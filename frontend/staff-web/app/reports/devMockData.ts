import type {
  BranchSalesReport,
  DailyCloseReport,
  OperatingResult,
  OwnerNotificationLog,
} from "@/lib/api";

export type ReportsDevMock = {
  report: BranchSalesReport;
  dailyCloseReports: DailyCloseReport[];
  operatingResult: OperatingResult;
  notificationsByReport: Record<string, OwnerNotificationLog[]>;
};

/**
 * Geçici rapor görsel değerlendirme katmanı. Yalnız development build'lerinde veya
 * localhost üzerinde etkinleşir; gerçek deployment hostlarında hiçbir zaman çalışmaz.
 * Kaldırmak için bu dosyayı ve reports page'deki tek entegrasyon bloğunu silmek yeterlidir.
 */
export function isReportsDevPreviewEnabled(): boolean {
  if (process.env.NODE_ENV === "development") return true;
  if (typeof window === "undefined") return false;
  return window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";
}

export function hasMeaningfulReportData(report: BranchSalesReport): boolean {
  return (
    report.grossSalesMinorUnits !== 0 ||
    report.netSalesMinorUnits !== 0 ||
    report.refundTotalMinorUnits !== 0 ||
    report.orderCount !== 0 ||
    report.productBreakdown.length > 0 ||
    report.categoryBreakdown.length > 0 ||
    report.hourlyDistribution.some((row) => row.orderCount > 0 || row.revenueMinorUnits > 0)
  );
}

export function hasMeaningfulOperatingResult(result: OperatingResult | null): boolean {
  return Boolean(
    result &&
      (result.grossSalesMinorUnits !== 0 ||
        result.refundTotalMinorUnits !== 0 ||
        result.netSalesMinorUnits !== 0 ||
        result.approvedExpensesMinorUnits !== 0 ||
        result.netOperatingResultMinorUnits !== 0),
  );
}

function isoDateDaysAgo(daysAgo: number): string {
  const date = new Date();
  date.setDate(date.getDate() - daysAgo);
  return date.toISOString().slice(0, 10);
}

export function createReportsDevMock(baseReport: BranchSalesReport): ReportsDevMock {
  const dailyGross = [4_120_000, 5_360_000, 4_890_000, 6_240_000, 5_780_000, 6_630_000, 4_875_000];
  const dailyRefund = [210_000, 330_000, 185_000, 420_000, 275_000, 310_000, 377_000];
  const dailyOrders = [104, 131, 119, 148, 139, 157, 126];

  const dailyCloseReports: DailyCloseReport[] = dailyGross.map((gross, index) => {
    const daysAgo = dailyGross.length - index - 1;
    const businessDate = isoDateDaysAgo(daysAgo);
    const refund = dailyRefund[index];
    const orderCount = dailyOrders[index];
    return {
      id: `dev-close-${businessDate}`,
      branchId: baseReport.branchId,
      branchName: baseReport.branchName,
      businessDate,
      periodStart: `${businessDate}T00:00:00`,
      periodEnd: `${businessDate}T23:59:59`,
      grossSalesMinorUnits: gross,
      refundTotalMinorUnits: refund,
      netSalesMinorUnits: gross - refund,
      orderCount,
      acceptedOrderCount: Math.max(0, orderCount - 8),
      rejectedOrderCount: 8,
      averageOrderValueMinorUnits: Math.round(gross / orderCount),
      tableVisitCount: Math.round(orderCount * 0.74),
      status: daysAgo === 0 ? "PREVIEW" : "FINAL",
      generatedAt: `${businessDate}T23:59:59`,
    };
  });

  const notificationsByReport = Object.fromEntries(
    dailyCloseReports
      .filter((row) => row.status === "FINAL")
      .map((row) => [
        row.id,
        [
          {
            id: `dev-notification-${row.id}`,
            recipientEmail: "yonetici@example.local",
            channel: "EMAIL" as const,
            status: "SENT" as const,
            errorMessage: null,
            triggeredBy: "AUTO" as const,
            attemptedAt: row.generatedAt,
          },
        ],
      ]),
  );

  return {
    report: {
      ...baseReport,
      from: isoDateDaysAgo(6),
      to: isoDateDaysAgo(0),
      grossSalesMinorUnits: 4_875_000,
      netSalesMinorUnits: 4_498_000,
      refundTotalMinorUnits: 377_000,
      orderCount: 126,
      acceptedOrderCount: 118,
      rejectedOrderCount: 8,
      averageOrderValueMinorUnits: 38_690,
      tableVisitCount: 94,
      guestCountTotal: 173,
      guestCountRecordedVisitCount: 94,
      averagePreparationSeconds: 780,
      completedOrderCount: 112,
      hourlyDistribution: [
        { hourOfDay: 9, orderCount: 3, revenueMinorUnits: 90_000 },
        { hourOfDay: 10, orderCount: 4, revenueMinorUnits: 125_000 },
        { hourOfDay: 11, orderCount: 7, revenueMinorUnits: 225_000 },
        { hourOfDay: 12, orderCount: 15, revenueMinorUnits: 520_000 },
        { hourOfDay: 13, orderCount: 18, revenueMinorUnits: 680_000 },
        { hourOfDay: 14, orderCount: 13, revenueMinorUnits: 470_000 },
        { hourOfDay: 15, orderCount: 6, revenueMinorUnits: 210_000 },
        { hourOfDay: 16, orderCount: 5, revenueMinorUnits: 175_000 },
        { hourOfDay: 17, orderCount: 7, revenueMinorUnits: 260_000 },
        { hourOfDay: 18, orderCount: 10, revenueMinorUnits: 390_000 },
        { hourOfDay: 19, orderCount: 12, revenueMinorUnits: 480_000 },
        { hourOfDay: 20, orderCount: 10, revenueMinorUnits: 430_000 },
        { hourOfDay: 21, orderCount: 7, revenueMinorUnits: 340_000 },
        { hourOfDay: 22, orderCount: 4, revenueMinorUnits: 230_000 },
        { hourOfDay: 23, orderCount: 2, revenueMinorUnits: 130_000 },
        { hourOfDay: 0, orderCount: 1, revenueMinorUnits: 55_000 },
        { hourOfDay: 1, orderCount: 1, revenueMinorUnits: 30_000 },
        { hourOfDay: 2, orderCount: 1, revenueMinorUnits: 35_000 },
      ],
      productBreakdown: [
        { productId: "dev-product-1", productName: "Izgara Dana Antrikot", quantitySold: 28, revenueMinorUnits: 868_000 },
        { productId: "dev-product-2", productName: "Karışık Izgara", quantitySold: 31, revenueMinorUnits: 744_000 },
        { productId: "dev-product-3", productName: "Dana Lokum", quantitySold: 24, revenueMinorUnits: 612_000 },
        { productId: "dev-product-4", productName: "Tavuk Şiş", quantitySold: 34, revenueMinorUnits: 476_000 },
        { productId: "dev-product-5", productName: "Mantı", quantitySold: 29, revenueMinorUnits: 406_000 },
        { productId: "dev-product-6", productName: "Sezar Salata", quantitySold: 26, revenueMinorUnits: 312_000 },
        { productId: "dev-product-7", productName: "Cheesecake", quantitySold: 22, revenueMinorUnits: 242_000 },
        { productId: "dev-product-8", productName: "Türk Kahvesi", quantitySold: 41, revenueMinorUnits: 184_000 },
      ],
      categoryBreakdown: [
        { categoryId: "dev-category-1", categoryName: "Ana Yemekler", revenueMinorUnits: 2_218_000 },
        { categoryId: "dev-category-2", categoryName: "Izgaralar", revenueMinorUnits: 1_356_000 },
        { categoryId: "dev-category-3", categoryName: "Başlangıçlar", revenueMinorUnits: 548_000 },
        { categoryId: "dev-category-4", categoryName: "Tatlılar", revenueMinorUnits: 421_000 },
        { categoryId: "dev-category-5", categoryName: "İçecekler", revenueMinorUnits: 332_000 },
      ],
    },
    dailyCloseReports,
    operatingResult: {
      branchId: baseReport.branchId,
      branchName: baseReport.branchName,
      from: isoDateDaysAgo(6),
      to: isoDateDaysAgo(0),
      grossSalesMinorUnits: 4_875_000,
      refundTotalMinorUnits: 377_000,
      netSalesMinorUnits: 4_498_000,
      approvedExpensesMinorUnits: 2_140_000,
      netOperatingResultMinorUnits: 2_358_000,
    },
    notificationsByReport,
  };
}
