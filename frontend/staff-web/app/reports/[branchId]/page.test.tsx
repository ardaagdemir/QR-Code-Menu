import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/ToastProvider";
import type { BranchSalesReport, BusinessContact, ReportNotificationLog, StaffContext } from "@/lib/api";
import ReportsPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({}),
}));

vi.mock("@/components/layout/AppShell", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const me = vi.hoisted(() => vi.fn());
const getBranchSalesReport = vi.hoisted(() => vi.fn());
const getDailyCloseReports = vi.hoisted(() => vi.fn());
const getOperatingResult = vi.hoisted(() => vi.fn());
const getBusinessHours = vi.hoisted(() => vi.fn());
const listBusinessContacts = vi.hoisted(() => vi.fn());
const createBusinessContact = vi.hoisted(() => vi.fn());
const updateBusinessContact = vi.hoisted(() => vi.fn());
const deleteBusinessContact = vi.hoisted(() => vi.fn());
const getReportNotifications = vi.hoisted(() => vi.fn());
const resendMonthlyReportNotifications = vi.hoisted(() => vi.fn());
const resendOwnerNotifications = vi.hoisted(() => vi.fn());
const getOwnerNotifications = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    me,
    getBranchSalesReport,
    getDailyCloseReports,
    getOperatingResult,
    getBusinessHours,
    listBusinessContacts,
    createBusinessContact,
    updateBusinessContact,
    deleteBusinessContact,
    getReportNotifications,
    resendMonthlyReportNotifications,
    resendOwnerNotifications,
    getOwnerNotifications,
  };
});

function staffContext(overrides: Partial<StaffContext> = {}): StaffContext {
  return {
    staffUserId: "staff-1",
    businessId: "business-1",
    email: "admin@example.com",
    role: "BUSINESS_ADMIN",
    branchIds: ["branch-1"],
    businessName: "Meydan Bistro",
    branches: [{ id: "branch-1", name: "Şube" }],
    activeBranchId: "branch-1",
    activeBranchName: "Şube",
    activeBranchTimeZone: "Europe/Istanbul",
    ...overrides,
  };
}

function branchSalesReport(overrides: Partial<BranchSalesReport> = {}): BranchSalesReport {
  return {
    branchId: "branch-1",
    branchName: "Şube",
    from: "2026-08-26",
    to: "2026-08-26",
    grossSalesMinorUnits: 0,
    netSalesMinorUnits: 0,
    refundTotalMinorUnits: 0,
    refundCount: 0,
    orderCount: 0,
    acceptedOrderCount: 0,
    rejectedOrderCount: 0,
    averageOrderValueMinorUnits: 0,
    tableVisitCount: 0,
    guestCountTotal: 0,
    guestCountRecordedVisitCount: 0,
    averagePreparationSeconds: 0,
    completedOrderCount: 0,
    productBreakdown: [],
    categoryBreakdown: [],
    hourlyDistribution: [],
    ...overrides,
  };
}

function contact(overrides: Partial<BusinessContact> = {}): BusinessContact {
  return {
    id: "contact-1",
    name: "Ayşe Yılmaz",
    phone: "+905551112233",
    email: "ayse@example.com",
    whatsappEnabled: false,
    dailyReportRecipient: true,
    monthlyReportRecipient: false,
    ...overrides,
  };
}

function reportNotification(overrides: Partial<ReportNotificationLog> = {}): ReportNotificationLog {
  return {
    id: "notif-1",
    reportType: "DAILY",
    dailyCloseReportId: "report-1",
    period: "2026-08-26",
    recipientEmail: "owner@example.com",
    status: "SENT",
    errorMessage: null,
    triggeredBy: "AUTO",
    attemptedAt: "2026-08-26T19:05:00Z",
    ...overrides,
  };
}

beforeEach(() => {
  me.mockReset();
  getBranchSalesReport.mockReset();
  getDailyCloseReports.mockReset();
  getOperatingResult.mockReset();
  getBusinessHours.mockReset();
  listBusinessContacts.mockReset();
  createBusinessContact.mockReset();
  updateBusinessContact.mockReset();
  deleteBusinessContact.mockReset();
  getReportNotifications.mockReset();
  resendMonthlyReportNotifications.mockReset();
  resendOwnerNotifications.mockReset();
  getOwnerNotifications.mockReset();

  me.mockResolvedValue(staffContext());
  getBranchSalesReport.mockResolvedValue(branchSalesReport());
  getDailyCloseReports.mockResolvedValue([]);
  getOperatingResult.mockRejectedValue(new Error("not needed for these tests"));
  getBusinessHours.mockResolvedValue([]);
  listBusinessContacts.mockResolvedValue([]);
  getReportNotifications.mockResolvedValue([]);
  getOwnerNotifications.mockResolvedValue([]);
});

describe("BranchReportPage - Rapor Alıcıları görünürlüğü", () => {
  it("shows the section and its contacts for BUSINESS_ADMIN", async () => {
    me.mockResolvedValue(staffContext({ role: "BUSINESS_ADMIN" }));
    listBusinessContacts.mockResolvedValue([contact()]);

    render(
      <ToastProvider>
        <ReportsPage />
      </ToastProvider>,
    );

    await screen.findByText("Rapor Alıcıları");
    screen.getByText("Ayşe Yılmaz");
  });

  it("hides the section for BRANCH_MANAGER (no BUSINESS_SETTINGS_MANAGE permission)", async () => {
    me.mockResolvedValue(staffContext({ role: "BRANCH_MANAGER" }));

    render(
      <ToastProvider>
        <ReportsPage />
      </ToastProvider>,
    );

    await screen.findByText("Gün Sonu Kapanışları");
    expect(screen.queryByText("Rapor Alıcıları")).toBeNull();
  });
});

describe("BranchReportPage - Rapor Alıcısı düzenleme/silme", () => {
  it("opens the edit dialog pre-filled and submits the update", async () => {
    listBusinessContacts.mockResolvedValue([contact()]);
    updateBusinessContact.mockResolvedValue(contact({ name: "Ayşe Demir" }));

    render(
      <ToastProvider>
        <ReportsPage />
      </ToastProvider>,
    );
    await screen.findByText("Ayşe Yılmaz");

    fireEvent.click(screen.getByRole("button", { name: "Düzenle" }));

    const dialog = within(await screen.findByRole("dialog"));
    await dialog.findByRole("heading", { name: "Alıcıyı Düzenle" });
    fireEvent.change(dialog.getByLabelText("Ad", { exact: false }), { target: { value: "Ayşe Demir" } });
    fireEvent.click(dialog.getByRole("button", { name: "Kaydet" }));

    await waitFor(() => expect(updateBusinessContact).toHaveBeenCalledTimes(1));
    expect(updateBusinessContact).toHaveBeenCalledWith(
      "contact-1",
      expect.objectContaining({ name: "Ayşe Demir", email: "ayse@example.com" }),
    );
  });

  it("deletes the contact after confirmation and refreshes the list", async () => {
    listBusinessContacts.mockResolvedValueOnce([contact()]).mockResolvedValueOnce([]);
    deleteBusinessContact.mockResolvedValue(undefined);

    render(
      <ToastProvider>
        <ReportsPage />
      </ToastProvider>,
    );
    await screen.findByText("Ayşe Yılmaz");

    fireEvent.click(screen.getByRole("button", { name: "Sil" }));
    await screen.findByText(/adlı alıcıyı silmek istediğinize emin misiniz/);

    const confirmButtons = screen.getAllByRole("button", { name: "Sil" });
    fireEvent.click(confirmButtons[confirmButtons.length - 1]);

    await waitFor(() => expect(deleteBusinessContact).toHaveBeenCalledWith("contact-1"));
    await waitFor(() => expect(screen.queryByText("Ayşe Yılmaz")).toBeNull());
  });
});

describe("BranchReportPage - Rapor Bildirimleri", () => {
  it("shows DAILY and MONTHLY rows with the correct type/period/status labels", async () => {
    getReportNotifications.mockResolvedValue([
      reportNotification({ id: "n1", reportType: "DAILY", dailyCloseReportId: "report-1", period: "2026-08-26", status: "SENT" }),
      reportNotification({ id: "n2", reportType: "MONTHLY", dailyCloseReportId: null, period: "2026-07-01", status: "FAILED" }),
    ]);

    render(
      <ToastProvider>
        <ReportsPage />
      </ToastProvider>,
    );

    await screen.findByText("Günlük Rapor");
    screen.getByText("Aylık Rapor");
    screen.getByText("Temmuz 2026");
    screen.getByText("Başarısız");
    screen.getByText("Yeniden Dene");
  });

  it("retries a FAILED monthly row via the manual monthly resend endpoint", async () => {
    getReportNotifications.mockResolvedValue([
      reportNotification({ id: "n2", reportType: "MONTHLY", dailyCloseReportId: null, period: "2026-07-01", status: "FAILED" }),
    ]);
    resendMonthlyReportNotifications.mockResolvedValue([]);

    render(
      <ToastProvider>
        <ReportsPage />
      </ToastProvider>,
    );
    await screen.findByText("Yeniden Dene");
    fireEvent.click(screen.getByRole("button", { name: "Yeniden Dene" }));

    await waitFor(() => expect(resendMonthlyReportNotifications).toHaveBeenCalledWith("2026-07"));
  });

  it("resends a SENT daily row via the existing per-report resend endpoint", async () => {
    getReportNotifications.mockResolvedValue([
      reportNotification({ id: "n1", reportType: "DAILY", dailyCloseReportId: "report-1", period: "2026-08-26", status: "SENT" }),
    ]);
    resendOwnerNotifications.mockResolvedValue([]);

    render(
      <ToastProvider>
        <ReportsPage />
      </ToastProvider>,
    );
    await screen.findByText("Tekrar Gönder");
    fireEvent.click(screen.getByRole("button", { name: "Tekrar Gönder" }));

    await waitFor(() => expect(resendOwnerNotifications).toHaveBeenCalledWith("report-1"));
  });

  it("shows at most 5 notification rows per page and paginates the rest", async () => {
    getReportNotifications.mockResolvedValue(
      Array.from({ length: 6 }, (_, index) =>
        reportNotification({ id: `n${index}`, recipientEmail: `user${index}@example.com` }),
      ),
    );

    render(
      <ToastProvider>
        <ReportsPage />
      </ToastProvider>,
    );

    await screen.findByText("user0@example.com");
    expect(screen.queryByText("user5@example.com")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Sonraki" }));

    await screen.findByText("user5@example.com");
    expect(screen.queryByText("user0@example.com")).toBeNull();
  });
});

describe("BranchReportPage - Rapor Alıcıları pagination", () => {
  it("shows at most 5 contact rows per page and paginates the rest", async () => {
    listBusinessContacts.mockResolvedValue(
      Array.from({ length: 6 }, (_, index) => contact({ id: `c${index}`, name: `Alıcı ${index + 1}` })),
    );

    render(
      <ToastProvider>
        <ReportsPage />
      </ToastProvider>,
    );

    await screen.findByText("Alıcı 1");
    screen.getByText("Alıcı 5");
    expect(screen.queryByText("Alıcı 6")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Sonraki" }));

    await screen.findByText("Alıcı 6");
    expect(screen.queryByText("Alıcı 1")).toBeNull();
  });
});

describe("BranchReportPage - Analiz kartı dönem seçimi", () => {
  it("switching a card's period to Haftalık re-fetches the sales report for a wider range", async () => {
    // "Bu Hafta" spans Monday through today, so on a real Monday `from === to` and this
    // assertion would flake with the system clock. Pin the clock to a known Wednesday
    // (2026-08-26) so the week always has a strictly earlier start date.
    // Only fake `Date` - RTL's `waitFor`/`findBy*` rely on real timers to poll.
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-08-26T12:00:00Z"));

    try {
      render(
        <ToastProvider>
          <ReportsPage />
        </ToastProvider>,
      );

      await waitFor(() => expect(getBranchSalesReport).toHaveBeenCalledTimes(1));

      // Saatlik Satış Dağılımı / Ürün Bazında Satış / Kategori Bazında Ciro each render their
      // own "Haftalık" toggle button in that DOM order - the second one belongs to Ürün Bazında Satış.
      const weeklyButtons = screen.getAllByRole("button", { name: "Haftalık" });
      expect(weeklyButtons).toHaveLength(3);
      fireEvent.click(weeklyButtons[1]);

      await waitFor(() => expect(getBranchSalesReport).toHaveBeenCalledTimes(2));
      const [from, to] = getBranchSalesReport.mock.calls[1];
      expect(from).not.toBe(to);
    } finally {
      vi.useRealTimers();
    }
  });
});
