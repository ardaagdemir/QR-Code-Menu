import type { InputHTMLAttributes } from "react";
import styles from "./Input.module.css";

type Props = InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean };

/** Copied from customer-web/components/ui/Input.tsx (Bölüm 14/19.1: shared
 * token/component approach). */
export default function Input({ className, invalid, ...rest }: Props) {
  const classes = [styles.input, invalid ? styles.invalid : null, className].filter(Boolean).join(" ");
  return <input className={classes} {...rest} />;
}
