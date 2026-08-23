import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { useOrderHistory } from "./useOrderHistory";

function tokensOf(entries: { token: string }[]): string[] {
  return entries.map((entry) => entry.token);
}

describe("useOrderHistory", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("accumulates tokens across multiple orders instead of overwriting the previous one", () => {
    const { result } = renderHook(() => useOrderHistory("table-1"));

    act(() => result.current.addToken("order-1"));
    act(() => result.current.addToken("order-2"));

    expect(tokensOf(result.current.entries)).toEqual(["order-1", "order-2"]);
  });

  it("records when each token was added, for display purposes", () => {
    const { result } = renderHook(() => useOrderHistory("table-1"));

    act(() => result.current.addToken("order-1"));

    expect(result.current.entries).toEqual([{ token: "order-1", addedAt: expect.any(String) }]);
    expect(Number.isNaN(new Date(result.current.entries[0].addedAt as string).getTime())).toBe(false);
  });

  it("survives unmount/remount of the component tree (e.g. navigating to tracking and back to the menu)", () => {
    const first = renderHook(() => useOrderHistory("table-1"));
    act(() => first.result.current.addToken("order-1"));
    first.unmount();

    // A fresh mount, as happens when TableVisitPage re-renders after the customer
    // navigates back from /order/track/[token] to /t/[token] - nothing here reads from
    // the unmounted hook's React state, only from localStorage.
    const second = renderHook(() => useOrderHistory("table-1"));

    expect(tokensOf(second.result.current.entries)).toEqual(["order-1"]);
  });

  it("keeps history scoped per table and reloads it when tableId changes", () => {
    const { result, rerender } = renderHook(({ tableId }) => useOrderHistory(tableId), {
      initialProps: { tableId: "table-1" },
    });
    act(() => result.current.addToken("order-1"));

    rerender({ tableId: "table-2" });
    expect(result.current.entries).toEqual([]);

    rerender({ tableId: "table-1" });
    expect(tokensOf(result.current.entries)).toEqual(["order-1"]);
  });

  it("removes a token (e.g. after the backend reports it no longer exists) without touching the others", () => {
    const { result } = renderHook(() => useOrderHistory("table-1"));
    act(() => result.current.addToken("order-1"));
    act(() => result.current.addToken("order-2"));

    act(() => result.current.removeToken("order-1"));

    expect(tokensOf(result.current.entries)).toEqual(["order-2"]);
  });

  it("does not leak Masa 8's order history into Masa 9, even in the same browser/branch", () => {
    const masa8 = renderHook(() => useOrderHistory("table-8"));
    act(() => masa8.result.current.addToken("masa8-order"));
    masa8.unmount();

    // Customer scans Masa 9's QR next - same browser, same branch, different table.
    const masa9 = renderHook(() => useOrderHistory("table-9"));
    expect(masa9.result.current.entries).toEqual([]);

    act(() => masa9.result.current.addToken("masa9-order"));
    masa9.unmount();

    // Masa 8's QR is scanned again later - its own history must still be there, and
    // must not have picked up Masa 9's order.
    const masa8Again = renderHook(() => useOrderHistory("table-8"));
    expect(tokensOf(masa8Again.result.current.entries)).toEqual(["masa8-order"]);
  });
});
