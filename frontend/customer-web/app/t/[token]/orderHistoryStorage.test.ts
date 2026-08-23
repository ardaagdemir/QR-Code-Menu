import { beforeEach, describe, expect, it } from "vitest";
import { addOrderToken, readOrderTokens, removeOrderToken } from "./orderHistoryStorage";

describe("orderHistoryStorage", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("returns an empty list when nothing was ever stored", () => {
    expect(readOrderTokens("table-1")).toEqual([]);
  });

  it("persists new order tokens without overwriting previous ones", () => {
    addOrderToken("table-1", "token-a");
    addOrderToken("table-1", "token-b");

    expect(readOrderTokens("table-1")).toEqual(["token-a", "token-b"]);
  });

  it("does not duplicate the same token when it is added again", () => {
    addOrderToken("table-1", "token-a");
    addOrderToken("table-1", "token-a");

    expect(readOrderTokens("table-1")).toEqual(["token-a"]);
  });

  it("scopes storage per table, not per branch (Masa 8 vs Masa 9 isolation)", () => {
    addOrderToken("table-8", "token-a");
    addOrderToken("table-9", "token-b");

    expect(readOrderTokens("table-8")).toEqual(["token-a"]);
    expect(readOrderTokens("table-9")).toEqual(["token-b"]);
  });

  it("silently drops corrupted storage instead of throwing", () => {
    window.localStorage.setItem("qrmenu.orderHistory.table.table-1", "not json");

    expect(readOrderTokens("table-1")).toEqual([]);
  });

  it("removes only the matching token, keeping the rest", () => {
    addOrderToken("table-1", "token-a");
    addOrderToken("table-1", "token-b");

    removeOrderToken("table-1", "token-a");

    expect(readOrderTokens("table-1")).toEqual(["token-b"]);
  });

  it("purges legacy branch-scoped history instead of leaking it into any table's list", () => {
    // Old (buggy) format: keyed by branchId, shared across every table in the branch.
    window.localStorage.setItem("qrmenu.orderHistory.branch-1", JSON.stringify(["leaked-token"]));

    expect(readOrderTokens("table-8")).toEqual([]);
    expect(readOrderTokens("table-9")).toEqual([]);
    expect(window.localStorage.getItem("qrmenu.orderHistory.branch-1")).toBeNull();
  });

  it("does not purge the new table-scoped keys as if they were legacy data", () => {
    addOrderToken("table-8", "token-a");

    // Triggers the legacy-cleanup scan again via another read.
    readOrderTokens("table-9");

    expect(readOrderTokens("table-8")).toEqual(["token-a"]);
  });
});
