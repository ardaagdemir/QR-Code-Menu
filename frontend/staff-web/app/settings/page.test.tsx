import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/ToastProvider";
import type { Business, Branch, StaffContext } from "@/lib/api";
import SettingsPage from "./page";

vi.mock("@/components/layout/AppShell", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const me = vi.hoisted(() => vi.fn());
const getBusiness = vi.hoisted(() => vi.fn());
const listBranches = vi.hoisted(() => vi.fn());
const getBusinessHours = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    me,
    getBusiness,
    listBranches,
    getBusinessHours,
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
    branches: [],
    activeBranchId: "branch-1",
    activeBranchName: "Merkez",
    activeBranchTimeZone: "Europe/Istanbul",
    ...overrides,
  };
}

function business(overrides: Partial<Business> = {}): Business {
  return {
    id: "business-1",
    name: "Meydan Bistro",
    active: true,
    defaultCurrency: "TRY",
    defaultTimeZone: "Europe/Istanbul",
    createdAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function branch(overrides: Partial<Branch> = {}): Branch {
  return {
    id: "branch-1",
    businessId: "business-1",
    name: "Merkez",
    active: true,
    orderingEnabled: true,
    openNow: true,
    address: "Bahçelievler Mah.",
    timezone: "Europe/Istanbul",
    deliveryModel: "WAITER_DELIVERY",
    storeAcceptanceTimeoutSeconds: 300,
    ...overrides,
  };
}

beforeEach(() => {
  me.mockReset();
  getBusiness.mockReset();
  listBranches.mockReset();
  getBusinessHours.mockReset();
  getBusiness.mockResolvedValue(business());
  listBranches.mockResolvedValue([branch()]);
  getBusinessHours.mockResolvedValue([]);
});

describe("SettingsPage - rol bazlı tab görünürlüğü", () => {
  it("BUSINESS_ADMIN için İşletme ve Şube tab'larını gösterir, varsayılan İşletme'dir", async () => {
    me.mockResolvedValue(staffContext({ role: "BUSINESS_ADMIN" }));

    render(
      <ToastProvider>
        <SettingsPage />
      </ToastProvider>,
    );

    expect((await screen.findByRole("tab", { name: "İşletme" })).getAttribute("aria-selected")).toBe("true");
    expect(screen.getByRole("tab", { name: "Şube" }).getAttribute("aria-selected")).toBe("false");
    await screen.findByRole("heading", { name: "İşletme Adı" });
  });

  it("Şube tab'ına geçince Şube Ayarları içeriği görünür", async () => {
    me.mockResolvedValue(staffContext({ role: "BUSINESS_ADMIN" }));

    render(
      <ToastProvider>
        <SettingsPage />
      </ToastProvider>,
    );

    const subeTab = await screen.findByRole("tab", { name: "Şube" });
    fireEvent.click(subeTab);

    await screen.findByText("Çalışma Saatleri");
  });

  it("BRANCH_MANAGER için tab çubuğu görünmez, doğrudan Şube içeriği gelir", async () => {
    me.mockResolvedValue(staffContext({ role: "BRANCH_MANAGER" }));

    render(
      <ToastProvider>
        <SettingsPage />
      </ToastProvider>,
    );

    await screen.findByText("Şube Ayarları");
    expect(screen.queryByRole("tab", { name: "İşletme" })).toBeNull();
    expect(screen.queryByRole("tab", { name: "Şube" })).toBeNull();
    // BRANCH_MANAGER yalnız ORDERING_TOGGLE'a sahip - Şube Bilgileri/Çalışma Saatleri gizli.
    await waitFor(() => expect(screen.queryByText("Şube Bilgileri")).toBeNull());
  });
});
