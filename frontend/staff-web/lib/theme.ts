export type Theme = "light" | "dark";

export const THEME_STORAGE_KEY = "qrmenu-staff-theme";

/** Inlined verbatim into app/layout.tsx's blocking <script> - keep the two in sync by
 * hand (a real module import isn't usable there, it has to be a plain string the
 * browser executes before first paint). Only ever applies a *stored* choice - falls
 * back to light, never reads prefers-color-scheme, so system dark mode can't flash the
 * page dark before this runs. */
export const THEME_INIT_SCRIPT = `(function(){try{var t=localStorage.getItem(${JSON.stringify(
  THEME_STORAGE_KEY,
)});if(t==="dark"){document.documentElement.setAttribute("data-theme","dark");}}catch(e){}})();`;

const listeners = new Set<() => void>();

/** For useSyncExternalStore in ThemeToggle - the DOM's data-theme attribute (set by the
 * blocking init script before hydration, then by applyTheme afterwards) is the external
 * store; this notifies subscribers whenever applyTheme changes it. */
export function subscribeTheme(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getThemeSnapshot(): Theme {
  return document.documentElement.getAttribute("data-theme") === "dark" ? "dark" : "light";
}

/** SSR/pre-hydration snapshot - matches the shell's light default (blocking script may
 * have already flipped the live DOM to dark by the time React hydrates; useSyncExternalStore
 * reconciles that without a hydration-mismatch warning). */
export function getServerThemeSnapshot(): Theme {
  return "light";
}

export function applyTheme(theme: Theme) {
  document.documentElement.setAttribute("data-theme", theme);
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // localStorage may be unavailable (private mode/quota) - theme still applies for this load.
  }
  listeners.forEach((listener) => listener());
}
