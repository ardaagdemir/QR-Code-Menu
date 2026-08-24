export const SIDEBAR_COLLAPSED_STORAGE_KEY = "qrmenu-staff-sidebar-collapsed";

const listeners = new Set<() => void>();

/** Same useSyncExternalStore + localStorage pattern as lib/theme.ts - needed because
 * AppShell is mounted fresh by every page (no shared layout), so plain useState would
 * reset the collapsed sidebar back to expanded on every navigation. */
export function subscribeSidebarCollapsed(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getSidebarCollapsedSnapshot(): boolean {
  try {
    return window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY) === "1";
  } catch {
    return false;
  }
}

export function getServerSidebarCollapsedSnapshot(): boolean {
  return false;
}

export function setSidebarCollapsed(collapsed: boolean) {
  try {
    window.localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE_KEY, collapsed ? "1" : "0");
  } catch {
    // localStorage may be unavailable (private mode/quota) - collapse still applies for this load.
  }
  listeners.forEach((listener) => listener());
}
