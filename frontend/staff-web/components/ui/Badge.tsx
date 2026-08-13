import type { ReactNode } from "react";
import styles from "./Badge.module.css";

type Tone = "neutral" | "success" | "danger" | "warning" | "info";

export default function Badge({ children, tone = "neutral" }: { children: ReactNode; tone?: Tone }) {
  return <span className={[styles.badge, styles[tone]].join(" ")}>{children}</span>;
}
