import type { TextareaHTMLAttributes } from "react";
import styles from "./Textarea.module.css";

type Props = TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean };

/** Copied from customer-web/components/ui/Textarea.tsx (Bölüm 14/19.1: shared
 * token/component approach). */
export default function Textarea({ className, invalid, rows = 3, ...rest }: Props) {
  const classes = [styles.textarea, invalid ? styles.invalid : null, className].filter(Boolean).join(" ");
  return <textarea className={classes} rows={rows} {...rest} />;
}
