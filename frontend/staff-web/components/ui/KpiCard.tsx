import type { ReactNode } from "react";
import styles from "./KpiCard.module.css";

type Tone = "neutral" | "danger" | "success";

type Props = {
  label: string;
  value: ReactNode;
  tone?: Tone;
  hint?: string;
};

/**
 * Bölüm 19.3 "Dashboard"/"Raporlama": KPI cards ile güçlü görsel hiyerarşi.
 * `reports.module.css`'teki ad-hoc StatCard deseninin shared component'e
 * çıkarılmış hali - dashboard ve raporlama sayfaları arasında paylaşılır.
 */
export default function KpiCard({ label, value, tone = "neutral", hint }: Props) {
  return (
    <div className={[styles.card, styles[tone]].join(" ")}>
      <span className={styles.label}>{label}</span>
      <span className={styles.value}>{value}</span>
      {hint ? <span className={styles.hint}>{hint}</span> : null}
    </div>
  );
}
