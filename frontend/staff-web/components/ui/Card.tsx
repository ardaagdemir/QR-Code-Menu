import type { HTMLAttributes, ReactNode } from "react";
import styles from "./Card.module.css";

type Padding = "sm" | "md" | "lg";

type Props = HTMLAttributes<HTMLDivElement> & {
  padding?: Padding;
  children: ReactNode;
};

/** Copied from customer-web/components/ui/Card.tsx (Bölüm 14/19.1: shared
 * token/component approach). */
export default function Card({ padding = "md", className, children, ...rest }: Props) {
  const classes = [styles.card, styles[padding], className].filter(Boolean).join(" ");
  return (
    <div className={classes} {...rest}>
      {children}
    </div>
  );
}
