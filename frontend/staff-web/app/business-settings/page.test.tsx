import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/ToastProvider";
import type { Business, BusinessContact } from "@/lib/api";
import BusinessSettingsPage from "./page";

// AppShell fetches /me and needs next/navigation - none of that is relevant to the
// double-submit guard under test here, so it's swapped for a passthrough.
vi.mock("@/components/layout/AppShell", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const getBusiness = vi.hoisted(() => vi.fn());
const updateBusinessSettings = vi.hoisted(() => vi.fn());
const updateBusinessName = vi.hoisted(() => vi.fn());
const listBusinessContacts = vi.hoisted(() => vi.fn());
const createBusinessContact = vi.hoisted(() => vi.fn());
const updateBusinessContact = vi.hoisted(() => vi.fn());
const deleteBusinessContact = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    getBusiness,
    updateBusinessSettings,
    updateBusinessName,
    listBusinessContacts,
    createBusinessContact,
    updateBusinessContact,
    deleteBusinessContact,
  };
});

const patchStaffContext = vi.hoisted(() => vi.fn());
vi.mock("@/lib/staffContextStore", () => ({ patchStaffContext }));

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

beforeEach(() => {
  getBusiness.mockReset();
  updateBusinessSettings.mockReset();
  updateBusinessName.mockReset();
  listBusinessContacts.mockReset();
  createBusinessContact.mockReset();
  updateBusinessContact.mockReset();
  deleteBusinessContact.mockReset();
  patchStaffContext.mockReset();
  getBusiness.mockResolvedValue(business());
  listBusinessContacts.mockResolvedValue([]);
});

describe("BusinessSettingsPage - İşletme Adı", () => {
  it("loads the current name, saves a trimmed value, and pushes it into the shared staff context", async () => {
    updateBusinessName.mockResolvedValue(business({ name: "Yeni İsim" }));

    render(
      <ToastProvider>
        <BusinessSettingsPage />
      </ToastProvider>,
    );

    const nameField = (await screen.findByDisplayValue("Meydan Bistro")) as HTMLInputElement;
    fireEvent.change(nameField, { target: { value: "  Yeni İsim  " } });
    fireEvent.click(screen.getByRole("button", { name: "Adı Kaydet" }));

    await waitFor(() => expect(updateBusinessName).toHaveBeenCalledWith("Yeni İsim"));
    await waitFor(() => expect(patchStaffContext).toHaveBeenCalledWith({ businessName: "Yeni İsim" }));
  });

  it("rejects a blank name without calling the API", async () => {
    render(
      <ToastProvider>
        <BusinessSettingsPage />
      </ToastProvider>,
    );

    const nameField = (await screen.findByDisplayValue("Meydan Bistro")) as HTMLInputElement;
    fireEvent.change(nameField, { target: { value: "   " } });
    fireEvent.click(screen.getByRole("button", { name: "Adı Kaydet" }));

    await screen.findByText("İşletme adı boş olamaz.");
    expect(updateBusinessName).not.toHaveBeenCalled();
  });
});

/**
 * Regression for the reported bug: rapid/repeated submits of the "Ayarları Kaydet" form
 * fired two overlapping updateBusinessSettings requests, and the backend 400'd one of
 * them. `disabled={savingSettings}` alone doesn't close this - a second submit event that
 * lands before React commits that prop update still reaches the handler. The fix adds a
 * ref checked/set synchronously at the top of handleSaveSettings.
 */
describe("BusinessSettingsPage - Ayarları Kaydet çift gönderim koruması", () => {
  it("sends only one update request when the form is submitted twice before the first request resolves", async () => {
    let resolveUpdate: (value: Business) => void = () => {};
    updateBusinessSettings.mockImplementation(
      () =>
        new Promise<Business>((resolve) => {
          resolveUpdate = resolve;
        }),
    );

    const { container } = render(
      <ToastProvider>
        <BusinessSettingsPage />
      </ToastProvider>,
    );
    await screen.findByDisplayValue("TRY");

    const form = container.querySelector("form");
    if (!form) {
      throw new Error("Ayarlar formu bulunamadı.");
    }

    // Two submit events dispatched back-to-back, before either the state update or the
    // network request has a chance to settle - the same race a fast double-click produces.
    fireEvent.submit(form);
    fireEvent.submit(form);

    expect(updateBusinessSettings).toHaveBeenCalledTimes(1);

    resolveUpdate(business());
    await waitFor(() =>
      expect((screen.getByText("Ayarları Kaydet") as HTMLButtonElement).disabled).toBe(false),
    );

    // Once the first request has resolved and the guard is released, a later, distinct
    // submit must go through normally.
    fireEvent.submit(form);
    expect(updateBusinessSettings).toHaveBeenCalledTimes(2);
  });
});

describe("BusinessSettingsPage - Rapor Alıcısı düzenleme", () => {
  it("opens the edit dialog pre-filled with the contact's current values and submits the update", async () => {
    listBusinessContacts.mockResolvedValue([contact()]);
    updateBusinessContact.mockResolvedValue(contact({ name: "Ayşe Demir" }));

    render(
      <ToastProvider>
        <BusinessSettingsPage />
      </ToastProvider>,
    );
    await screen.findByText("Ayşe Yılmaz");

    fireEvent.click(screen.getByRole("button", { name: "Düzenle" }));

    const dialog = within(await screen.findByRole("dialog"));
    await dialog.findByRole("heading", { name: "Kişiyi Düzenle" });
    expect((dialog.getByLabelText("Ad", { exact: false }) as HTMLInputElement).value).toBe("Ayşe Yılmaz");
    expect(dialog.queryByLabelText("Telefon")).toBeNull();
    expect((dialog.getByLabelText("E-posta") as HTMLInputElement).value).toBe("ayse@example.com");

    fireEvent.change(dialog.getByLabelText("Ad", { exact: false }), { target: { value: "Ayşe Demir" } });
    fireEvent.click(dialog.getByRole("button", { name: "Kaydet" }));

    await waitFor(() => expect(updateBusinessContact).toHaveBeenCalledTimes(1));
    // Telefon alanı UI'dan kaldırıldı, ama düzenleme formunun yönetmediği mevcut değer korunarak gönderilmeli.
    expect(updateBusinessContact).toHaveBeenCalledWith(
      "contact-1",
      expect.objectContaining({ name: "Ayşe Demir", phone: "+905551112233", email: "ayse@example.com" }),
    );
    await waitFor(() => expect(screen.queryByRole("heading", { name: "Kişiyi Düzenle" })).toBeNull());
  });
});

describe("BusinessSettingsPage - Rapor Alıcısı silme", () => {
  it("asks for confirmation before deleting and does not call the API on cancel", async () => {
    listBusinessContacts.mockResolvedValue([contact()]);

    render(
      <ToastProvider>
        <BusinessSettingsPage />
      </ToastProvider>,
    );
    await screen.findByText("Ayşe Yılmaz");

    fireEvent.click(screen.getByRole("button", { name: "Sil" }));
    await screen.findByText(/Ayşe Yılmaz.*adlı kişiyi silmek istediğinize emin misiniz/);

    fireEvent.click(screen.getByRole("button", { name: "Vazgeç" }));

    expect(deleteBusinessContact).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(screen.queryByText(/adlı kişiyi silmek istediğinize emin misiniz/)).toBeNull(),
    );
  });

  it("deletes the contact and refreshes the list on confirm", async () => {
    listBusinessContacts.mockResolvedValueOnce([contact()]).mockResolvedValueOnce([]);
    deleteBusinessContact.mockResolvedValue(undefined);

    render(
      <ToastProvider>
        <BusinessSettingsPage />
      </ToastProvider>,
    );
    await screen.findByText("Ayşe Yılmaz");

    fireEvent.click(screen.getByRole("button", { name: "Sil" }));
    await screen.findByText(/adlı kişiyi silmek istediğinize emin misiniz/);

    const dialogConfirmButtons = screen.getAllByRole("button", { name: "Sil" });
    fireEvent.click(dialogConfirmButtons[dialogConfirmButtons.length - 1]);

    await waitFor(() => expect(deleteBusinessContact).toHaveBeenCalledWith("contact-1"));
    await waitFor(() => expect(listBusinessContacts).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.queryByText("Ayşe Yılmaz")).toBeNull());
  });
});
