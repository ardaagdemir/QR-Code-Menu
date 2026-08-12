/** Bölüm 19.3 kasa/KDS kartlarındaki "ne kadar süredir bekliyor" göstergesi. */
export type WaitingUrgency = "normal" | "warning" | "danger";

const WARNING_THRESHOLD_MINUTES = 5;
const DANGER_THRESHOLD_MINUTES = 10;

export function elapsedMinutes(iso: string, nowMs: number): number {
  const elapsedMs = nowMs - new Date(iso).getTime();
  return Math.max(0, Math.floor(elapsedMs / 60000));
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

export function waitingUrgency(iso: string, nowMs: number): WaitingUrgency {
  const minutes = elapsedMinutes(iso, nowMs);
  if (minutes >= DANGER_THRESHOLD_MINUTES) {
    return "danger";
  }
  if (minutes >= WARNING_THRESHOLD_MINUTES) {
    return "warning";
  }
  return "normal";
}
