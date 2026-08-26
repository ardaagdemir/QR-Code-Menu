import type { ReactNode } from "react";
import styles from "./Badge.module.css";

type Tone = "neutral" | "success" | "danger" | "warning" | "info";

export default function Badge({
  children,
  tone = "neutral",
  title,
}: {
  children: ReactNode;
  tone?: Tone;
  title?: string;
}) {
  return (
    <span className={[styles.badge, styles[tone]].join(" ")} title={title}>
      {children}
    </span>
  );
}
