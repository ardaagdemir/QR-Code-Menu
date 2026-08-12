import type { TextareaHTMLAttributes } from "react";
import styles from "./Textarea.module.css";

type Props = TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean };

/** Bare styled textarea - compose inside FormField for the label/hint/error contract. */
export default function Textarea({ className, invalid, rows = 3, ...rest }: Props) {
  const classes = [styles.textarea, invalid ? styles.invalid : null, className].filter(Boolean).join(" ");
  return <textarea className={classes} rows={rows} {...rest} />;
}
