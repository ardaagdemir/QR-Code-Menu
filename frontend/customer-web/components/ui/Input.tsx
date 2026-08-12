import type { InputHTMLAttributes } from "react";
import styles from "./Input.module.css";

type Props = InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean };

/** Bare styled text input - compose inside FormField for the label/hint/error contract. */
export default function Input({ className, invalid, ...rest }: Props) {
  const classes = [styles.input, invalid ? styles.invalid : null, className].filter(Boolean).join(" ");
  return <input className={classes} {...rest} />;
}
