import type { StaffContext } from "./api";

const listeners = new Set<() => void>();
let current: StaffContext | null = null;

/** Same subscribe/snapshot pattern as lib/sidebarCollapse.ts, in-memory rather than
 * localStorage-backed. AppShell is mounted fresh by every page and always re-fetches /me
 * on mount, but a page that just changed something on the StaffContext (e.g. business name
 * in business-settings) needs the topbar to reflect it immediately, without waiting for the
 * next navigation's refetch - patchStaffContext lets it push the update directly. */
export function subscribeStaffContext(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getStaffContextSnapshot(): StaffContext | null {
  return current;
}

export function setStaffContext(context: StaffContext | null) {
  current = context;
  listeners.forEach((listener) => listener());
}

export function patchStaffContext(patch: Partial<StaffContext>) {
  if (!current) {
    return;
  }
  setStaffContext({ ...current, ...patch });
}
