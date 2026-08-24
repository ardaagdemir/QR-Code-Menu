import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/ToastProvider";
import type { Business } from "@/lib/api";
import BusinessSettingsPage from "./page";

// AppShell fetches /me and needs next/navigation - none of that is relevant to the
// double-submit guard under test here, so it's swapped for a passthrough.
vi.mock("@/components/layout/AppShell", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const getBusiness = vi.hoisted(() => vi.fn());
const updateBusinessSettings = vi.hoisted(() => vi.fn());
const listBusinessContacts = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    getBusiness,
    updateBusinessSettings,
    listBusinessContacts,
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

beforeEach(() => {
  getBusiness.mockReset();
  updateBusinessSettings.mockReset();
  listBusinessContacts.mockReset();
  getBusiness.mockResolvedValue(business());
  listBusinessContacts.mockResolvedValue([]);
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
