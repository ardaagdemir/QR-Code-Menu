import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, type Cart, type Menu, type TableVisit } from "@/lib/api";
import TableVisitPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ token: "qr-token" }),
}));

const checkInWithQrToken = vi.hoisted(() => vi.fn());
const getMenu = vi.hoisted(() => vi.fn());
const getCart = vi.hoisted(() => vi.fn());
const getPopularProductIds = vi.hoisted(() => vi.fn());
const addCartItem = vi.hoisted(() => vi.fn());
const removeCartItem = vi.hoisted(() => vi.fn());
const createPaymentIntent = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    checkInWithQrToken,
    getMenu,
    getCart,
    getPopularProductIds,
    addCartItem,
    removeCartItem,
    createPaymentIntent,
  };
});

const EXPIRED_MESSAGE = "Oturumunuz sona erdi. Yeni sipariş için masadaki QR kodunu tekrar okutun.";

function visit(overrides: Partial<TableVisit> = {}): TableVisit {
  return {
    tableVisitId: "visit-1",
    businessId: "business-1",
    branchId: "branch-1",
    tableId: "table-1",
    businessName: "Test Business",
    branchName: "Test Branch",
    tableLabel: "Masa 1",
    startedAt: "2026-08-21T10:00:00.000Z",
    guestCount: 2, // non-null so the one-time guest-count prompt sheet doesn't auto-open
    ...overrides,
  };
}

function menu(): Menu {
  return {
    branchId: "branch-1",
    categories: [
      {
        id: "cat-1",
        name: "Ana Yemekler",
        products: [
          {
            id: "product-1",
            name: "Köfte",
            description: null,
            imageUrl: null,
            priceMinorUnits: 5000,
            taxRatePercent: 10,
            availability: "AVAILABLE",
            estimatedPreparationMinutes: null,
            allergens: [],
            optionGroups: [],
          },
        ],
      },
    ],
  };
}

function emptyCart(): Cart {
  return { tableVisitId: "visit-1", orderId: null, status: null, totalMinorUnits: 0, items: [], orderTrackingToken: null };
}

function cartWithOneItem(): Cart {
  return {
    tableVisitId: "visit-1",
    orderId: "order-1",
    status: "DRAFT",
    totalMinorUnits: 5000,
    items: [
      {
        id: "item-1",
        productId: "product-1",
        productName: "Köfte",
        unitPriceMinorUnits: 5000,
        quantity: 1,
        lineTotalMinorUnits: 5000,
        options: [],
      },
    ],
    orderTrackingToken: null,
  };
}

async function addProductToCart() {
  fireEvent.click(await screen.findByRole("button", { name: "Köfte" }));
  fireEvent.click(await screen.findByRole("button", { name: /Sepete Ekle/ }));
}

describe("TableVisitPage - customer TableVisit expiry", () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
    vi.resetAllMocks();
    // jsdom has no IntersectionObserver - page.tsx's scroll-spy effect only needs the
    // constructor + observe/disconnect to exist, it never asserts on real intersections.
    (global as unknown as { IntersectionObserver: unknown }).IntersectionObserver = class {
      observe() {}
      disconnect() {}
      unobserve() {}
    };
    getPopularProductIds.mockResolvedValue([]);
  });

  it("an active visit can add an item to the cart with no expired-session message shown", async () => {
    checkInWithQrToken.mockResolvedValue(visit());
    getMenu.mockResolvedValue(menu());
    getCart.mockResolvedValue(emptyCart());
    addCartItem.mockResolvedValue(cartWithOneItem());

    render(<TableVisitPage />);
    await addProductToCart();

    await waitFor(() => expect(screen.getByText("Sepetim")).toBeTruthy());
    expect(screen.queryByText(EXPIRED_MESSAGE)).toBeNull();
    // The menu itself must still be there - this is not the old full-page state swap.
    expect(screen.getByRole("button", { name: "Köfte" })).toBeTruthy();
  });

  it("shows the exact expired-session message on a 410 from the backend, without hiding the menu", async () => {
    checkInWithQrToken.mockResolvedValue(visit());
    getMenu.mockResolvedValue(menu());
    getCart.mockResolvedValue(emptyCart());
    addCartItem.mockRejectedValue(new ApiError("Table visit expired", 410));

    render(<TableVisitPage />);
    await addProductToCart();

    expect(await screen.findByText(EXPIRED_MESSAGE)).toBeTruthy();
    // Menu must remain browsable - the requirement is "menu still viewable, just can't order".
    expect(screen.getByRole("button", { name: "Köfte" })).toBeTruthy();
  });

  it("does not confuse a stale/gone visit (404) with an expired-but-viewable one (410)", async () => {
    checkInWithQrToken.mockResolvedValue(visit());
    getMenu.mockResolvedValue(menu());
    getCart.mockResolvedValue(emptyCart());
    addCartItem.mockRejectedValue(new ApiError("Table visit not found", 404));

    render(<TableVisitPage />);
    await addProductToCart();

    // The pre-existing 404 ("stale visit") path still takes over the full page - unlike
    // 410, this case is genuinely unrecoverable in place.
    await waitFor(() => expect(screen.getByText("Devam edilemiyor")).toBeTruthy());
    expect(screen.queryByText(EXPIRED_MESSAGE)).toBeNull();
  });

  it("shows the exact expired-session message when checkout (payment start) hits a 410, keeping the cart visible", async () => {
    checkInWithQrToken.mockResolvedValue(visit());
    getMenu.mockResolvedValue(menu());
    getCart.mockResolvedValue(cartWithOneItem());
    createPaymentIntent.mockRejectedValue(new ApiError("Table visit expired", 410));

    render(<TableVisitPage />);
    fireEvent.click(await screen.findByText("Sepetim"));
    fireEvent.click(await screen.findByText("Ödemeye Geç"));

    expect(await screen.findByText(EXPIRED_MESSAGE)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Köfte" })).toBeTruthy();
  });
});
