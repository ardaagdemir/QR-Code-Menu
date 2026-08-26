import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

afterEach(() => {
  cleanup();
});

// jsdom doesn't implement matchMedia (see lib/viewport.ts) - stub it so components that
// read the live desktop breakpoint don't crash under test. Defaults to "not matching"
// (mobile); tests that need to simulate a specific breakpoint override this per-case.
if (typeof window.matchMedia !== "function") {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}
