import type { CSSProperties } from "react";
import styles from "./Skeleton.module.css";

type Props = {
  width?: string;
  height?: string;
  radius?: string;
  className?: string;
};

/** A single pulsing placeholder block - compose several to build a skeleton screen. */
export default function Skeleton({ width = "100%", height = "1rem", radius, className }: Props) {
  const style: CSSProperties = { width, height, borderRadius: radius ?? "var(--radius-sm)" };
  return <span aria-hidden="true" className={[styles.skeleton, className].filter(Boolean).join(" ")} style={style} />;
}
