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
