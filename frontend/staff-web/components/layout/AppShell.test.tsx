import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/ToastProvider";
import type { StaffContext } from "@/lib/api";
import AppShell from "./AppShell";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
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
});

/**
 * Regression for the reported bug: a role without permission for a route (e.g. CASHIER
 * hitting /refunds directly) used to get bounced to the login page on the resulting 403,
 * identically to an expired session (401). Only 401 should do that now - a 403 while the
 * session is still valid must show this inline message inside the authenticated shell.
 * See isSessionExpired/isAccessDenied in lib/api.ts and their call sites in
 * app/{cashier,orders,refunds}/**\/page.tsx.
 */
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
