import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/ToastProvider";
import type { Branch, Business, StaffContext, StaffUser } from "@/lib/api";
import PlatformAdminBusinessDetailPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ businessId: "business-1" }),
}));

vi.mock("@/components/layout/AppShell", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const getPlatformBusiness = vi.hoisted(() => vi.fn());
const listPlatformBranches = vi.hoisted(() => vi.fn());
const listPlatformStaffUsers = vi.hoisted(() => vi.fn());
const me = vi.hoisted(() => vi.fn());
const updatePlatformBusinessName = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    getPlatformBusiness,
    listPlatformBranches,
    listPlatformStaffUsers,
    me,
    updatePlatformBusinessName,
  };
});

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

function staffContext(overrides: Partial<StaffContext> = {}): StaffContext {
  return {
    staffUserId: "staff-1",
    businessId: "business-1",
    email: "platform-admin@qrmenu.local",
    role: "PLATFORM_ADMIN",
    branchIds: [],
    businessName: "Meydan Bistro",
    branches: [],
    activeBranchId: null,
    activeBranchName: null,
    activeBranchTimeZone: null,
    ...overrides,
  };
}

beforeEach(() => {
  getPlatformBusiness.mockReset();
  listPlatformBranches.mockReset();
  listPlatformStaffUsers.mockReset();
  me.mockReset();
  updatePlatformBusinessName.mockReset();
  getPlatformBusiness.mockResolvedValue(business());
  listPlatformBranches.mockResolvedValue([] as Branch[]);
  listPlatformStaffUsers.mockResolvedValue([] as StaffUser[]);
  me.mockResolvedValue(staffContext());
});

/**
 * Regression for the reported gap: PLATFORM_ADMIN had no way to rename a business - the
 * detail page only showed business.name as a read-only PageHeader title. "Düzenle" opens a
 * dialog pre-filled with the current name, submitting calls the platform-admin rename
 * endpoint (not the staff one - this page manages another tenant's business), and the page
 * re-renders with the new name from the response, no separate refetch required.
 */
describe("PlatformAdminBusinessDetailPage - İşletme adını düzenleme", () => {
  it("opens the edit dialog pre-filled with the current name and submits the rename", async () => {
    updatePlatformBusinessName.mockResolvedValue(business({ name: "Yeni İşletme Adı" }));

    render(
      <ToastProvider>
        <PlatformAdminBusinessDetailPage />
      </ToastProvider>,
    );

    await screen.findByRole("heading", { name: "Meydan Bistro" });
    fireEvent.click(screen.getByRole("button", { name: "Düzenle" }));

    await screen.findByRole("heading", { name: "İşletme Adını Düzenle" });
    expect((screen.getByRole("textbox") as HTMLInputElement).value).toBe("Meydan Bistro");

    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Yeni İşletme Adı" } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));

    await waitFor(() => expect(updatePlatformBusinessName).toHaveBeenCalledWith("business-1", "Yeni İşletme Adı"));
    await screen.findByRole("heading", { name: "Yeni İşletme Adı" });
    expect(screen.queryByRole("heading", { name: "İşletme Adını Düzenle" })).toBeNull();
  });

  it("rejects a blank name without calling the API", async () => {
    render(
      <ToastProvider>
        <PlatformAdminBusinessDetailPage />
      </ToastProvider>,
    );

    await screen.findByRole("heading", { name: "Meydan Bistro" });
    fireEvent.click(screen.getByRole("button", { name: "Düzenle" }));

    await screen.findByRole("heading", { name: "İşletme Adını Düzenle" });
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "   " } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));

    await screen.findByText("İşletme adı girin.");
    expect(updatePlatformBusinessName).not.toHaveBeenCalled();
  });
});

function branch(overrides: Partial<Branch> = {}): Branch {
  return {
    id: "branch-1",
    businessId: "business-1",
    name: "Merkez Şube",
    active: true,
    orderingEnabled: true,
    openNow: true,
    address: null,
    timezone: null,
    deliveryModel: "WAITER_DELIVERY",
    storeAcceptanceTimeoutSeconds: 120,
    ...overrides,
  };
}

function staffUser(overrides: Partial<StaffUser> = {}): StaffUser {
  return {
    id: "staff-2",
    email: "kasiyer@qrmenu.local",
    role: "CASHIER",
    active: true,
    branchIds: ["branch-1"],
    createdAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

/**
 * Regression for the reported gap: the Kullanıcılar table gave no indication of which
 * branch a staff member belonged to (only a raw branchId, never shown). The table now
 * resolves branchIds[0] against the branches already loaded for this business detail
 * page - no separate branch-name lookup endpoint needed.
 */
describe("PlatformAdminBusinessDetailPage - Kullanıcılar şube kolonu", () => {
  it("shows the branch name for a staff user and a Pasif badge when the branch is inactive", async () => {
    listPlatformBranches.mockResolvedValue([
      branch({ id: "branch-1", name: "Merkez Şube", active: true }),
      branch({ id: "branch-2", name: "Kadıköy Şube", active: false }),
    ]);
    listPlatformStaffUsers.mockResolvedValue([
      staffUser({ id: "staff-2", email: "kasiyer@qrmenu.local", branchIds: ["branch-1"] }),
      staffUser({ id: "staff-3", email: "sorumlu@qrmenu.local", role: "BRANCH_MANAGER", branchIds: ["branch-2"] }),
    ]);

    render(
      <ToastProvider>
        <PlatformAdminBusinessDetailPage />
      </ToastProvider>,
    );

    const activeRow = (await screen.findByText("kasiyer@qrmenu.local")).closest("tr");
    expect(activeRow).not.toBeNull();
    expect(activeRow!.textContent).toContain("Merkez Şube");

    const inactiveRow = screen.getByText("sorumlu@qrmenu.local").closest("tr");
    expect(inactiveRow).not.toBeNull();
    expect(inactiveRow!.textContent).toContain("Kadıköy Şube");
    expect(inactiveRow!.textContent).toContain("Pasif");
  });

  it("shows a dash for PLATFORM_ADMIN rows since they have no branch", async () => {
    listPlatformBranches.mockResolvedValue([branch({ id: "branch-1", name: "Merkez Şube" })]);
    listPlatformStaffUsers.mockResolvedValue([
      staffUser({ id: "staff-4", email: "admin@qrmenu.local", role: "PLATFORM_ADMIN", branchIds: [] }),
    ]);

    render(
      <ToastProvider>
        <PlatformAdminBusinessDetailPage />
      </ToastProvider>,
    );

    const row = (await screen.findByText("admin@qrmenu.local")).closest("tr");
    expect(row).not.toBeNull();
    expect(row!.textContent).toContain("—");
  });
});
