import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/ToastProvider";
import type { Business } from "@/lib/api";
import BusinessTab from "./BusinessTab";

const getBusiness = vi.hoisted(() => vi.fn());
const updateBusinessSettings = vi.hoisted(() => vi.fn());
const updateBusinessName = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    getBusiness,
    updateBusinessSettings,
    updateBusinessName,
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

beforeEach(() => {
  getBusiness.mockReset();
  updateBusinessSettings.mockReset();
  updateBusinessName.mockReset();
  patchStaffContext.mockReset();
  getBusiness.mockResolvedValue(business());
});

describe("BusinessTab - İşletme Adı", () => {
  it("loads the current name, saves a trimmed value, and pushes it into the shared staff context", async () => {
    updateBusinessName.mockResolvedValue(business({ name: "Yeni İsim" }));

    render(
      <ToastProvider>
        <BusinessTab />
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
        <BusinessTab />
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
describe("BusinessTab - Ayarları Kaydet çift gönderim koruması", () => {
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
        <BusinessTab />
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
