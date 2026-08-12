import type { SelectHTMLAttributes } from "react";
import styles from "./Select.module.css";

type Props = SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean };

/** Copied from customer-web/components/ui/Select.tsx (Bölüm 14/19.1: shared
 * token/component approach). */
export default function Select({ className, invalid, children, ...rest }: Props) {
  const classes = [styles.select, invalid ? styles.invalid : null, className].filter(Boolean).join(" ");
  return (
    <select className={classes} {...rest}>
      {children}
    </select>
  );
}
