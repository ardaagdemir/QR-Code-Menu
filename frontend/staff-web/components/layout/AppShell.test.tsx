import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/ToastProvider";
import type { StaffContext } from "@/lib/api";
import { SIDEBAR_COLLAPSED_STORAGE_KEY } from "@/lib/sidebarCollapse";
import { patchStaffContext, setStaffContext } from "@/lib/staffContextStore";
import AppShell from "./AppShell";
import styles from "./AppShell.module.css";

const replace = vi.fn();
// A stable object, not a fresh literal per call - matches real next/navigation's
// memoized useRouter(). AppShell's /me effect depends on [router], so a fresh object per
// render (the naive mock) would re-run that effect on every render and refetch /me,
// clobbering any patchStaffContext update a test makes in between.
const routerMock = { replace };
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/refunds",
}));

const me = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return { ...actual, me };
});

function staffContext(overrides: Partial<StaffContext> = {}): StaffContext {
  return {
    staffUserId: "staff-1",
    businessId: "business-1",
    email: "cashier@qrmenu.local",
    role: "CASHIER",
    branchIds: ["branch-1"],
    businessName: "Meydan Bistro",
    branches: [{ id: "branch-1", name: "Merkez" }],
    activeBranchId: "branch-1",
    activeBranchName: "Merkez",
    activeBranchTimeZone: "Europe/Istanbul",
    ...overrides,
  };
}

function renderShell(accessDenied: boolean) {
  return render(
    <ToastProvider>
      <AppShell accessDenied={accessDenied}>
        <div>Sayfa İçeriği</div>
      </AppShell>
    </ToastProvider>,
  );
}

beforeEach(() => {
  replace.mockClear();
  me.mockReset();
  me.mockResolvedValue(staffContext());
  window.localStorage.clear();
  // Module-level singleton (see lib/staffContextStore.ts) - would otherwise leak the
  // previous test's context into this one's first paint.
  setStaffContext(null);
});

/** Stubs matchMedia("(min-width: 1024px)") with a fake MediaQueryList whose "matches"
 * can be flipped at will and fires real "change" listeners - lets tests drive
 * lib/viewport's live breakpoint tracking without touching real jsdom layout (jsdom
 * doesn't do CSS layout, so there's no real width to resize). */
function mockDesktopBreakpoint(initialMatches: boolean) {
  let matches = initialMatches;
  const listeners = new Set<(event: { matches: boolean }) => void>();
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    get matches() {
      return matches;
    },
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: (_event: string, listener: (event: { matches: boolean }) => void) => {
      listeners.add(listener);
    },
    removeEventListener: (_event: string, listener: (event: { matches: boolean }) => void) => {
      listeners.delete(listener);
    },
    dispatchEvent: vi.fn(),
  }));
  return {
    setDesktop(next: boolean) {
      matches = next;
      listeners.forEach((listener) => listener({ matches: next }));
    },
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

/**
 * Regression for the reported bug: a single shared handler used to flip both the mobile
 * drawer's open state and the desktop collapsed-rail preference on every click, with only
 * a CSS breakpoint deciding which one was visible. Opening the drawer on mobile therefore
 * silently persisted "collapsed" for desktop too, so widening the window (no refresh) later
 * showed a broken/unintended collapsed rail. See lib/viewport.ts and toggleDrawer/
 * toggleCollapsed in AppShell.tsx.
 */
describe("AppShell sidebar responsive state", () => {
  it("opening the mobile drawer does not persist the desktop collapse preference", async () => {
    mockDesktopBreakpoint(false);
    renderShell(false);
    await screen.findByText("Sayfa İçeriği");

    fireEvent.click(screen.getByRole("button", { name: "Menüyü aç" }));

    expect(localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY)).toBeNull();
    expect(screen.getByRole("button", { name: "Kenar çubuğunu daralt" })).toBeTruthy();
  });

  it("toggling the desktop collapse rail does not open the mobile drawer", async () => {
    mockDesktopBreakpoint(true);
    renderShell(false);
    await screen.findByText("Sayfa İçeriği");

    fireEvent.click(screen.getByRole("button", { name: "Kenar çubuğunu daralt" }));

    expect(localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY)).toBe("1");
    expect(document.querySelector(`.${styles.backdrop}`)).toBeNull();
  });

  it("resizing into desktop clears a stale mobile drawer-open state", async () => {
    const breakpoint = mockDesktopBreakpoint(false);
    renderShell(false);
    await screen.findByText("Sayfa İçeriği");

    fireEvent.click(screen.getByRole("button", { name: "Menüyü aç" }));
    expect(document.querySelector(`.${styles.backdrop}`)).toBeTruthy();

    act(() => {
      breakpoint.setDesktop(true);
    });

    await waitFor(() => expect(document.querySelector(`.${styles.backdrop}`)).toBeNull());
  });
});

/**
 * Regression for the reported bug: a role without permission for a route (e.g. CASHIER
 * hitting /refunds directly) used to get bounced to the login page on the resulting 403,
 * identically to an expired session (401). Only 401 should do that now - a 403 while the
 * session is still valid must show this inline message inside the authenticated shell.
 * See isSessionExpired/isAccessDenied in lib/api.ts and their call sites in
 * app/{cashier,orders,refunds}/**\/page.tsx.
 */
/**
 * Regression for the reported gap: business-settings had no way to update the business
 * name shown in the topbar without a full page reload, because AppShell used to keep the
 * fetched StaffContext in local useState. Now it reads from the shared staffContextStore,
 * so a page that renames the business can push the new name in directly via
 * patchStaffContext and see the topbar update immediately, in the same session.
 */
describe("AppShell topbar businessName", () => {
  it("updates immediately when patchStaffContext is called, without remounting", async () => {
    renderShell(false);
    await screen.findByText("Meydan Bistro");

    act(() => {
      patchStaffContext({ businessName: "Yeni İşletme Adı" });
    });

    expect(await screen.findByText("Yeni İşletme Adı")).toBeTruthy();
    expect(screen.queryByText("Meydan Bistro")).toBeNull();
  });
});

describe("AppShell accessDenied", () => {
  it("shows an inline access-denied message in place of the page content, without redirecting to login", async () => {
    renderShell(true);

    expect(await screen.findByText("Bu sayfaya erişim yetkiniz yok.")).toBeTruthy();
    expect(screen.queryByText("Sayfa İçeriği")).toBeNull();
    expect(replace).not.toHaveBeenCalled();
  });

  it("renders the page content normally when access is not denied", async () => {
    renderShell(false);

    expect(await screen.findByText("Sayfa İçeriği")).toBeTruthy();
    expect(screen.queryByText("Bu sayfaya erişim yetkiniz yok.")).toBeNull();
  });

  it("still redirects to login on a genuinely expired session (/me itself fails)", async () => {
    me.mockRejectedValue(new Error("session expired"));

    renderShell(false);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
  });
});
