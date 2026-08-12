import type { SelectHTMLAttributes } from "react";
import styles from "./Select.module.css";

type Props = SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean };

/** Bare styled select - compose inside FormField for the label/hint/error contract. */
export default function Select({ className, invalid, children, ...rest }: Props) {
  const classes = [styles.select, invalid ? styles.invalid : null, className].filter(Boolean).join(" ");
  return (
    <select className={classes} {...rest}>
      {children}
    </select>
  );
}
