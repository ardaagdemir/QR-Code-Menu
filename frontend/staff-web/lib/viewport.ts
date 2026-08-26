export const DESKTOP_BREAKPOINT_QUERY = "(min-width: 1024px)";

/** Mirrors the `@media (min-width: 1024px)` breakpoint in AppShell.module.css exactly -
 * keep the two in sync by hand if that breakpoint ever changes. */
function getDesktopMediaQueryList(): MediaQueryList | null {
  if (typeof window === "undefined") {
    return null;
  }
  return window.matchMedia(DESKTOP_BREAKPOINT_QUERY);
}

/** Same useSyncExternalStore pattern as lib/theme.ts / lib/sidebarCollapse.ts. Tracks the
 * desktop breakpoint live via matchMedia's own change event, so a plain browser resize
 * (no navigation/refresh) is picked up - a one-off `window.innerWidth` check at mount
 * would miss that. */
export function subscribeIsDesktop(listener: () => void) {
  const mql = getDesktopMediaQueryList();
  if (!mql) {
    return () => undefined;
  }
  mql.addEventListener("change", listener);
  return () => mql.removeEventListener("change", listener);
}

export function getIsDesktopSnapshot(): boolean {
  return getDesktopMediaQueryList()?.matches ?? false;
}

/** SSR/pre-hydration snapshot. Only ever used to decide whether to reset the mobile
 * drawer's open state (see AppShell) - never to gate rendered markup/classes, which stay
 * purely CSS-driven - so guessing "mobile" here can't cause a layout flash or hydration
 * mismatch either way. */
export function getServerIsDesktopSnapshot(): boolean {
  return false;
}
