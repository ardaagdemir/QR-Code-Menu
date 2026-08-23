/**
 * Calendar date (YYYY-MM-DD) in the browser/device's own local timezone - never
 * `date.toISOString().slice(0, 10)`, which reads the UTC calendar date instead.
 *
 * Do NOT use this for anything that reads or writes branch-scoped financial data
 * (Özet/Kasa/Raporlar, daily-close, expense dates) - the staff device's timezone is not
 * guaranteed to match the branch's configured timezone, and the backend's reporting
 * endpoints (ReportingService, DailyCloseService) always resolve "today" via
 * TenantService.resolveBranchTimeZone. Use `branchIsoDate` with the branch timezone from
 * StaffContext.activeBranchTimeZone (see lib/api.ts's `me()`) for those instead. This one
 * remains valid for genuinely device-local concerns (e.g. a live "now" ticker's own clock).
 */
export function localIsoDate(date: Date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * Calendar date (YYYY-MM-DD) in the given IANA branch timezone - what "today" must mean
 * for every branch-scoped financial read/write (Özet/Kasa/Raporlar, daily-close, expense
 * dates), so it agrees with the backend's own TenantService.resolveBranchTimeZone-based day
 * boundary regardless of which timezone the staff member's device happens to be in.
 * Falls back to the device's own local date only when no timezone is known yet (e.g. the
 * brief instant before `me()` has resolved) - callers should prefer to wait for
 * StaffContext.activeBranchTimeZone rather than lean on this fallback.
 */
export function branchIsoDate(timeZone: string | null | undefined, date: Date = new Date()): string {
  if (!timeZone) {
    return localIsoDate(date);
  }
  try {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).formatToParts(date);
    const year = parts.find((part) => part.type === "year")?.value;
    const month = parts.find((part) => part.type === "month")?.value;
    const day = parts.find((part) => part.type === "day")?.value;
    if (!year || !month || !day) {
      return localIsoDate(date);
    }
    return `${year}-${month}-${day}`;
  } catch {
    return localIsoDate(date);
  }
}

/**
 * A plain calendar Date built from the branch's own year/month/day (no timezone attached).
 * Backs preset date-range math (this week/this month start, etc.) - once the branch's
 * y/m/d is read via `branchIsoDate`, the arithmetic itself (setDate, getDay, ...) is pure
 * calendar math with no further timezone meaning, so it's safe to run through the device's
 * own local Date getters/setters from here on.
 */
export function branchCalendarDate(timeZone: string | null | undefined, date: Date = new Date()): Date {
  const [year, month, day] = branchIsoDate(timeZone, date).split("-").map(Number);
  return new Date(year, month - 1, day);
}

/** Bölüm 19.3 kasa/KDS kartlarındaki "ne kadar süredir bekliyor" göstergesi. */
export type WaitingUrgency = "normal" | "warning" | "danger";

const WARNING_THRESHOLD_MINUTES = 5;
const DANGER_THRESHOLD_MINUTES = 10;

/** Configurable-timeout eşiğinde erken uyarı, timeout'un bu oranına ulaşınca tetiklenir. */
const WARNING_RATIO_OF_TIMEOUT = 0.6;

export function elapsedMinutes(iso: string, nowMs: number): number {
  const elapsedMs = nowMs - new Date(iso).getTime();
  return Math.max(0, Math.floor(elapsedMs / 60000));
}

export function elapsedSeconds(iso: string, nowMs: number): number {
  const elapsedMs = nowMs - new Date(iso).getTime();
  return Math.max(0, Math.floor(elapsedMs / 1000));
}

export function formatElapsedMinutes(iso: string, nowMs: number): string {
  const minutes = elapsedMinutes(iso, nowMs);
  if (minutes < 1) {
    return "az önce";
  }
  if (minutes < 60) {
    return `${minutes} dk`;
  }
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return remainingMinutes === 0 ? `${hours} sa` : `${hours} sa ${remainingMinutes} dk`;
}

/**
 * `timeoutSeconds` verildiğinde (Section 6/27: branch bazında configurable kasa kabul
 * timeout'u), "danger/kritik" eşiği tam olarak o timeout'tur - "warning" ise o eşiğin
 * %60'ında, kasaya erken bir ısınma sinyali olarak tetiklenir. Verilmediğinde (KDS
 * mutfak hazırlama süresi gibi timeout kavramı olmayan yerler) sabit 5/10 dakikalık
 * eşiklere düşer.
 */
export function waitingUrgency(iso: string, nowMs: number, timeoutSeconds?: number): WaitingUrgency {
  if (timeoutSeconds != null) {
    const seconds = elapsedSeconds(iso, nowMs);
    if (seconds >= timeoutSeconds) {
      return "danger";
    }
    if (seconds >= timeoutSeconds * WARNING_RATIO_OF_TIMEOUT) {
      return "warning";
    }
    return "normal";
  }

  const minutes = elapsedMinutes(iso, nowMs);
  if (minutes >= DANGER_THRESHOLD_MINUTES) {
    return "danger";
  }
  if (minutes >= WARNING_THRESHOLD_MINUTES) {
    return "warning";
  }
  return "normal";
}
