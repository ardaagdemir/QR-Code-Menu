import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { OrderTracking } from "@/lib/api";
import OrderTrackingPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ token: "track-token" }),
  useRouter: () => ({ back: vi.fn() }),
}));

const getOrderTracking = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return { ...actual, getOrderTracking };
});

/** Minimal controllable stand-in for the browser's EventSource - lets a test observe
 * whether the page tore down and reopened the stream, without a real network connection. */
class FakeEventSource {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;
  static instances: FakeEventSource[] = [];

  readyState = FakeEventSource.OPEN;
  private listeners: Record<string, Array<() => void>> = {};

  constructor(public url: string) {
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, callback: () => void) {
    (this.listeners[type] ??= []).push(callback);
  }

  close() {
    this.readyState = FakeEventSource.CLOSED;
  }

  emitOrderStatus() {
    this.listeners["order-status"]?.forEach((callback) => callback());
  }
}

function tracking(overrides: Partial<OrderTracking> = {}): OrderTracking {
  return {
    orderId: "order-1",
    orderNumber: 7,
    status: "IN_KITCHEN",
    totalMinorUnits: 2000,
    deliveryModel: "WAITER_DELIVERY",
    latestRefundStatus: null,
    items: [],
    ...overrides,
  };
}

function setVisibilityState(state: "visible" | "hidden") {
  Object.defineProperty(document, "visibilityState", { value: state, writable: true, configurable: true });
  document.dispatchEvent(new Event("visibilitychange"));
}

describe("OrderTrackingPage - phone lock/wake resume", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    vi.stubGlobal("EventSource", FakeEventSource);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.resetAllMocks();
  });

  it("reloads fresh order state from the backend when the page becomes visible again", async () => {
    getOrderTracking.mockResolvedValue(tracking());
    render(<OrderTrackingPage />);
    await screen.findByText("Sipariş No: #7");
    expect(getOrderTracking).toHaveBeenCalledTimes(1);

    // Simulate the phone locking then unlocking - the tab never unmounts, but time has
    // passed and the customer expects to see the current state, not a stale snapshot.
    setVisibilityState("hidden");
    setVisibilityState("visible");

    await waitFor(() => expect(getOrderTracking).toHaveBeenCalledTimes(2));
  });

  it("does not reopen the SSE stream on resume if it never actually closed", async () => {
    getOrderTracking.mockResolvedValue(tracking());
    render(<OrderTrackingPage />);
    await screen.findByText("Sipariş No: #7");
    expect(FakeEventSource.instances).toHaveLength(1);

    setVisibilityState("visible");
    await waitFor(() => expect(getOrderTracking).toHaveBeenCalledTimes(2));

    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it("reconnects the SSE stream on resume if the connection had actually dropped", async () => {
    getOrderTracking.mockResolvedValue(tracking());
    render(<OrderTrackingPage />);
    await screen.findByText("Sipariş No: #7");
    expect(FakeEventSource.instances).toHaveLength(1);

    // A locked phone can silently drop the underlying connection - simulate that by
    // closing the stream the page is holding onto, then resuming.
    FakeEventSource.instances[0].close();
    setVisibilityState("visible");

    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(2));
  });

  it("still reflects live SSE-pushed updates after a reconnect", async () => {
    getOrderTracking.mockResolvedValueOnce(tracking({ status: "IN_KITCHEN" }));
    render(<OrderTrackingPage />);
    await screen.findByText("Sipariş No: #7");

    FakeEventSource.instances[0].close();
    getOrderTracking.mockResolvedValue(tracking({ status: "READY" }));
    setVisibilityState("visible");
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(2));

    getOrderTracking.mockResolvedValue(tracking({ status: "COMPLETED" }));
    FakeEventSource.instances[1].emitOrderStatus();

    await waitFor(() => expect(getOrderTracking).toHaveBeenCalledTimes(3));
  });
});
