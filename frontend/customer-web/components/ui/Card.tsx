import type { HTMLAttributes, ReactNode } from "react";
import styles from "./Card.module.css";

type Padding = "sm" | "md" | "lg";

type Props = HTMLAttributes<HTMLDivElement> & {
  padding?: Padding;
  children: ReactNode;
};

/** Shared surface container - base building block for KPI/summary cards, list rows and
 * panel sections (Bölüm 19.1: ortak Card pattern'i). */
export default function Card({ padding = "md", className, children, ...rest }: Props) {
  const classes = [styles.card, styles[padding], className].filter(Boolean).join(" ");
  return (
    <div className={classes} {...rest}>
      {children}
    </div>
  );
}
