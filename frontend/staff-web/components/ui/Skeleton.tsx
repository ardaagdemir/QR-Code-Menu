import type { CSSProperties } from "react";
import styles from "./Skeleton.module.css";

type Props = {
  width?: string;
  height?: string;
  radius?: string;
  className?: string;
};

/** Copied from customer-web/components/ui/Skeleton.tsx (Bölüm 14/19.1: shared
 * token/component approach). */
export default function Skeleton({ width = "100%", height = "1rem", radius, className }: Props) {
  const style: CSSProperties = { width, height, borderRadius: radius ?? "var(--radius-sm)" };
  return <span aria-hidden="true" className={[styles.skeleton, className].filter(Boolean).join(" ")} style={style} />;
}
