import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/ToastProvider";
import { ApiError, type StaffContext, type StaffUser } from "@/lib/api";
import StaffPage from "./page";

vi.mock("@/components/layout/AppShell", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const listStaffUsers = vi.hoisted(() => vi.fn());
const me = vi.hoisted(() => vi.fn());
const activateStaffUser = vi.hoisted(() => vi.fn());
const deactivateStaffUser = vi.hoisted(() => vi.fn());
const changeStaffUserRole = vi.hoisted(() => vi.fn());
const updateStaffUserEmail = vi.hoisted(() => vi.fn());
const resetStaffUserPassword = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    listStaffUsers,
    me,
    activateStaffUser,
    deactivateStaffUser,
    changeStaffUserRole,
    updateStaffUserEmail,
    resetStaffUserPassword,
  };
});

function staffUser(overrides: Partial<StaffUser> = {}): StaffUser {
  return {
    id: "staff-cashier-1",
    email: "cashier@example.com",
    role: "CASHIER",
    active: true,
    branchIds: ["branch-1"],
    createdAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function staffContext(overrides: Partial<StaffContext> = {}): StaffContext {
  return {
    staffUserId: "staff-admin-1",
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

beforeEach(() => {
  listStaffUsers.mockReset();
  me.mockReset();
  activateStaffUser.mockReset();
  deactivateStaffUser.mockReset();
  changeStaffUserRole.mockReset();
  updateStaffUserEmail.mockReset();
  resetStaffUserPassword.mockReset();
  me.mockResolvedValue(staffContext());
});

describe("StaffPage - satır aksiyonları", () => {
  it("kendi satırında hiçbir yönetim aksiyonu göstermez", async () => {
    listStaffUsers.mockResolvedValue([staffUser({ id: "staff-admin-1", email: "admin@example.com", role: "BUSINESS_ADMIN" })]);

    render(
      <ToastProvider>
        <StaffPage />
      </ToastProvider>,
    );

    await screen.findByText("admin@example.com");
    expect(screen.queryByRole("button", { name: /Düzenle/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /Aktifleştir/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /Devre Dışı Bırak/ })).toBeNull();
  });

  it("aktif bir personel için Düzenle / Şifre Sıfırla / Devre Dışı Bırak gösterir, Aktifleştir göstermez", async () => {
    listStaffUsers.mockResolvedValue([staffUser({ active: true })]);

    render(
      <ToastProvider>
        <StaffPage />
      </ToastProvider>,
    );

    await screen.findByText("cashier@example.com");
    expect(screen.getByRole("button", { name: /Düzenle/ })).toBeTruthy();
    expect(screen.getByRole("button", { name: /Şifre Sıfırla/ })).toBeTruthy();
    expect(screen.getByRole("button", { name: /Devre Dışı Bırak/ })).toBeTruthy();
    expect(screen.queryByRole("button", { name: /Aktifleştir/ })).toBeNull();
  });

  it("devre dışı bir personel için Aktifleştir gösterir, Devre Dışı Bırak göstermez", async () => {
    listStaffUsers.mockResolvedValue([staffUser({ active: false })]);

    render(
      <ToastProvider>
        <StaffPage />
      </ToastProvider>,
    );

    await screen.findByText("cashier@example.com");
    expect(screen.getByRole("button", { name: /Aktifleştir/ })).toBeTruthy();
    expect(screen.queryByRole("button", { name: /Devre Dışı Bırak/ })).toBeNull();
  });
});

describe("StaffPage - Aktifleştir", () => {
  it("onaylandığında activateStaffUser çağırır ve listeyi yeniler", async () => {
    listStaffUsers.mockResolvedValueOnce([staffUser({ active: false })]);
    listStaffUsers.mockResolvedValueOnce([staffUser({ active: true })]);
    activateStaffUser.mockResolvedValue(undefined);

    render(
      <ToastProvider>
        <StaffPage />
      </ToastProvider>,
    );

    await screen.findByRole("button", { name: /Aktifleştir/ });
    fireEvent.click(screen.getByRole("button", { name: /Aktifleştir/ }));

    await screen.findByRole("heading", { name: "Aktifleştir" });
    const confirmDialog = screen.getByRole("dialog");
    fireEvent.click(within(confirmDialog).getByRole("button", { name: "Aktifleştir" }));

    await waitFor(() => expect(activateStaffUser).toHaveBeenCalledWith("staff-cashier-1"));
    await waitFor(() => expect(listStaffUsers).toHaveBeenCalledTimes(2));
  });
});

describe("StaffPage - Düzenle", () => {
  it("e-postayı değiştirince yalnızca updateStaffUserEmail çağrılır", async () => {
    listStaffUsers.mockResolvedValue([staffUser()]);
    updateStaffUserEmail.mockResolvedValue(undefined);

    render(
      <ToastProvider>
        <StaffPage />
      </ToastProvider>,
    );

    await screen.findByText("cashier@example.com");
    fireEvent.click(screen.getByRole("button", { name: /Düzenle/ }));

    await screen.findByRole("heading", { name: "Personeli Düzenle" });
    const emailInput = screen.getByLabelText(/E-posta/) as HTMLInputElement;
    expect(emailInput.value).toBe("cashier@example.com");

    fireEvent.change(emailInput, { target: { value: "fixed@example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));

    await waitFor(() => expect(updateStaffUserEmail).toHaveBeenCalledWith("staff-cashier-1", "fixed@example.com"));
    expect(changeStaffUserRole).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByRole("heading", { name: "Personeli Düzenle" })).toBeNull());
  });

  it("rolü değiştirince yalnızca changeStaffUserRole çağrılır", async () => {
    listStaffUsers.mockResolvedValue([staffUser()]);
    changeStaffUserRole.mockResolvedValue(undefined);

    render(
      <ToastProvider>
        <StaffPage />
      </ToastProvider>,
    );

    await screen.findByText("cashier@example.com");
    fireEvent.click(screen.getByRole("button", { name: /Düzenle/ }));

    await screen.findByRole("heading", { name: "Personeli Düzenle" });
    fireEvent.change(screen.getByLabelText("Rol"), { target: { value: "BRANCH_MANAGER" } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));

    await waitFor(() => expect(changeStaffUserRole).toHaveBeenCalledWith("staff-cashier-1", "BRANCH_MANAGER"));
    expect(updateStaffUserEmail).not.toHaveBeenCalled();
  });

  it("e-posta zaten kullanımda hatasında (409) diyalog açık kalır ve hata mesajı gösterilir", async () => {
    listStaffUsers.mockResolvedValue([staffUser()]);
    updateStaffUserEmail.mockRejectedValue(new ApiError("Conflict", 409));

    render(
      <ToastProvider>
        <StaffPage />
      </ToastProvider>,
    );

    await screen.findByText("cashier@example.com");
    fireEvent.click(screen.getByRole("button", { name: /Düzenle/ }));

    await screen.findByRole("heading", { name: "Personeli Düzenle" });
    fireEvent.change(screen.getByLabelText(/E-posta/), { target: { value: "taken@example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));

    await screen.findByText(/zaten kullanımda/);
    expect(screen.getByRole("heading", { name: "Personeli Düzenle" })).toBeTruthy();
  });
});
