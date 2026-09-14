import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/ToastProvider";
import type { Branch, BranchBusinessHoursEntry, DayOfWeek } from "@/lib/api";
import BranchTab from "./BranchTab";

const listBranches = vi.hoisted(() => vi.fn());
const getBusinessHours = vi.hoisted(() => vi.fn());
const setBusinessHours = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    listBranches,
    getBusinessHours,
    setBusinessHours,
  };
});

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

function hoursEntry(dayOfWeek: DayOfWeek, overrides: Partial<BranchBusinessHoursEntry> = {}): BranchBusinessHoursEntry {
  return { dayOfWeek, openingTime: "09:00:00", closingTime: "22:00:00", closed: false, ...overrides };
}

const UNIFORM_WEEKDAY_HOURS: BranchBusinessHoursEntry[] = [
  hoursEntry("MONDAY"),
  hoursEntry("TUESDAY"),
  hoursEntry("WEDNESDAY"),
  hoursEntry("THURSDAY"),
  hoursEntry("FRIDAY"),
  hoursEntry("SATURDAY"),
  hoursEntry("SUNDAY", { closed: true, openingTime: null, closingTime: null }),
];

beforeEach(() => {
  listBranches.mockReset();
  getBusinessHours.mockReset();
  setBusinessHours.mockReset();
  listBranches.mockResolvedValue([branch()]);
});

describe("BranchTab - Çalışma Saatleri", () => {
  it("hafta içi günler aynıysa tek 'Hafta içi' satırında gösterir", async () => {
    getBusinessHours.mockResolvedValue(UNIFORM_WEEKDAY_HOURS);

    render(
      <ToastProvider>
        <BranchTab role="BUSINESS_ADMIN" />
      </ToastProvider>,
    );

    await screen.findByText("Çalışma Saatleri");
    expect(screen.getByText("Hafta içi")).toBeTruthy();
    expect(screen.queryByText("Pazartesi")).toBeNull();
    expect(screen.queryByText("Salı")).toBeNull();
  });

  it("hafta içi günler farklıysa satırı sessizce birleştirmez, 5 ayrı gün gösterir", async () => {
    const mixedHours = UNIFORM_WEEKDAY_HOURS.map((entry) =>
      entry.dayOfWeek === "WEDNESDAY" ? hoursEntry("WEDNESDAY", { closed: true, openingTime: null, closingTime: null }) : entry,
    );
    getBusinessHours.mockResolvedValue(mixedHours);

    render(
      <ToastProvider>
        <BranchTab role="BUSINESS_ADMIN" />
      </ToastProvider>,
    );

    await screen.findByText("Pazartesi");
    expect(screen.getByText("Salı")).toBeTruthy();
    expect(screen.getByText("Çarşamba")).toBeTruthy();
    expect(screen.getByText("Perşembe")).toBeTruthy();
    expect(screen.getByText("Cuma")).toBeTruthy();
    expect(screen.queryByText("Hafta içi")).toBeNull();
  });

  it("'Tüm günlere uygula' Pazartesi'nin saatlerini diğer tüm günlere kopyalar", async () => {
    const mixedHours = UNIFORM_WEEKDAY_HOURS.map((entry) =>
      entry.dayOfWeek === "WEDNESDAY" ? hoursEntry("WEDNESDAY", { closed: true, openingTime: null, closingTime: null }) : entry,
    );
    getBusinessHours.mockResolvedValue(mixedHours);
    setBusinessHours.mockResolvedValue([]);

    render(
      <ToastProvider>
        <BranchTab role="BUSINESS_ADMIN" />
      </ToastProvider>,
    );

    await screen.findByText("Pazartesi");
    fireEvent.click(screen.getByRole("button", { name: /Tüm günlere uygula/ }));

    // Genişletilmiş görünüm kapanır, tekrar tek "Hafta içi" satırına döner.
    await waitFor(() => expect(screen.getByText("Hafta içi")).toBeTruthy());
    expect(screen.queryByText("Çarşamba")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Saatleri Kaydet" }));

    await waitFor(() => expect(setBusinessHours).toHaveBeenCalledTimes(1));
    const savedHours = setBusinessHours.mock.calls[0][0] as BranchBusinessHoursEntry[];
    for (const entry of savedHours) {
      expect(entry.openingTime).toBe("09:00:00");
      expect(entry.closingTime).toBe("22:00:00");
      expect(entry.closed).toBe(false);
    }
  });

  it("BRANCH_MANAGER için Şube Bilgileri ve Çalışma Saatleri gizlidir, yalnız sipariş kabul toggle görünür", async () => {
    getBusinessHours.mockResolvedValue(UNIFORM_WEEKDAY_HOURS);

    render(
      <ToastProvider>
        <BranchTab role="BRANCH_MANAGER" />
      </ToastProvider>,
    );

    await screen.findByText("Şube Ayarları");
    expect(screen.queryByText("Şube Bilgileri")).toBeNull();
    expect(screen.queryByText("Çalışma Saatleri")).toBeNull();
    expect(screen.getByLabelText("Sipariş kabulünü aç veya kapat")).toBeTruthy();
  });
});
