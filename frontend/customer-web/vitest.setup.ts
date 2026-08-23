import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Without this, each render() in a test file stays mounted in jsdom's shared document
// for the rest of that file, so text/role queries in later tests can match leftover
// elements from earlier ones (surfaced by OrdersSheet.test.tsx: two tests render an
// order with the same default addedAt, causing "multiple elements found" errors).
afterEach(() => {
  cleanup();
});
