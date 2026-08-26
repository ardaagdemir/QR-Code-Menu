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
