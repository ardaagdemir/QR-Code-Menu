import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, type OrderTracking } from "@/lib/api";
import type { OrderHistoryEntry } from "./orderHistoryStorage";
import OrdersSheet from "./OrdersSheet";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

const getOrderTracking = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return { ...actual, getOrderTracking };
});

function tracking(overrides: Partial<OrderTracking> = {}): OrderTracking {
  return {
    orderId: "order-1",
    orderNumber: 42,
    status: "IN_KITCHEN",
    totalMinorUnits: 1000,
    deliveryModel: "WAITER_DELIVERY",
    latestRefundStatus: null,
    items: [],
    ...overrides,
  };
}

function entry(token: string, addedAt: string | null = "2026-08-21T10:00:00.000Z"): OrderHistoryEntry {
  return { token, addedAt };
}

afterEach(() => {
  vi.resetAllMocks();
});

describe("OrdersSheet", () => {
  it("shows an empty state when there is no persisted order history", async () => {
    render(<OrdersSheet entries={[]} onPrune={vi.fn()} onClose={vi.fn()} />);

    expect(await screen.findByText("Henüz siparişiniz yok")).toBeTruthy();
  });

  it("fetches every persisted token fresh from the backend and lists the newest order first", async () => {
    getOrderTracking.mockImplementation(async (token: string) =>
      token === "token-a" ? tracking({ orderNumber: 1 }) : tracking({ orderNumber: 2 }),
    );

    render(<OrdersSheet entries={[entry("token-a"), entry("token-b")]} onPrune={vi.fn()} onClose={vi.fn()} />);

    await waitFor(() => expect(screen.getByText("#2")).toBeTruthy());
    expect(screen.getByText("#1")).toBeTruthy();
  });

  it("silently prunes a token the backend no longer recognizes (404) instead of showing an error", async () => {
    const onPrune = vi.fn();
    getOrderTracking.mockImplementation(async (token: string) => {
      if (token === "stale-token") {
        throw new ApiError("not found", 404);
      }
      return tracking();
    });

    render(<OrdersSheet entries={[entry("stale-token"), entry("live-token")]} onPrune={onPrune} onClose={vi.fn()} />);

    await waitFor(() => expect(onPrune).toHaveBeenCalledWith("stale-token"));
    expect(screen.getByText("#42")).toBeTruthy();
    expect(screen.queryByText("Sipariş yüklenemedi.")).toBeNull();
  });

  it("keeps a token that failed with a transient (non-404) error instead of discarding it", async () => {
    const onPrune = vi.fn();
    getOrderTracking.mockImplementation(async () => {
      throw new ApiError("boom", 500);
    });

    render(<OrdersSheet entries={[entry("token-a")]} onPrune={onPrune} onClose={vi.fn()} />);

    expect(await screen.findByText("Sipariş yüklenemedi.")).toBeTruthy();
    expect(onPrune).not.toHaveBeenCalled();
  });

  it("shows a colored status label and, when known, the added-at date for each order", async () => {
    getOrderTracking.mockResolvedValue(tracking({ status: "READY" }));
    const addedAt = "2026-08-21T10:00:00.000Z";

    render(<OrdersSheet entries={[entry("token-a", addedAt)]} onPrune={vi.fn()} onClose={vi.fn()} />);

    expect(await screen.findByText("Hazır")).toBeTruthy();
    // Formatted with the same Intl formatter the component uses, so this is stable
    // regardless of which timezone the test happens to run in.
    const expectedLabel = new Intl.DateTimeFormat("tr-TR", {
      day: "numeric",
      month: "short",
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(addedAt));
    expect(screen.getByText(expectedLabel)).toBeTruthy();
  });

  it("shows the rejected order alongside its refund status", async () => {
    getOrderTracking.mockResolvedValue(tracking({ status: "REJECTED_BY_STORE", latestRefundStatus: "PROCESSING" }));

    render(<OrdersSheet entries={[entry("token-a")]} onPrune={vi.fn()} onClose={vi.fn()} />);

    expect(await screen.findByText("Reddedildi")).toBeTruthy();
    expect(screen.getByText("İade işleniyor")).toBeTruthy();
  });

  it("only labels a refund 'tamamlandı' when its own status is COMPLETED, never merely because the order was rejected", async () => {
    getOrderTracking.mockResolvedValue(tracking({ status: "REJECTED_BY_STORE", latestRefundStatus: "FAILED" }));

    render(<OrdersSheet entries={[entry("token-a")]} onPrune={vi.fn()} onClose={vi.fn()} />);

    expect(await screen.findByText("İade başarısız")).toBeTruthy();
    expect(screen.queryByText("İade tamamlandı")).toBeNull();
  });

  it("shows no refund badge for an order that was never refunded", async () => {
    getOrderTracking.mockResolvedValue(tracking({ status: "COMPLETED", latestRefundStatus: null }));

    render(<OrdersSheet entries={[entry("token-a")]} onPrune={vi.fn()} onClose={vi.fn()} />);

    await screen.findByText("Tamamlandı");
    expect(screen.queryByText(/İade/)).toBeNull();
  });
});
