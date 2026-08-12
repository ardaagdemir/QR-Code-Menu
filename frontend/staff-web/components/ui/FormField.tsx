"use client";

import { useId } from "react";
import type { ReactNode } from "react";
import styles from "./FormField.module.css";

type ControlProps = {
  id: string;
  "aria-describedby"?: string;
  "aria-invalid"?: boolean;
};

type Props = {
  label: string;
  hint?: string;
  error?: string;
  required?: boolean;
  children: (controlProps: ControlProps) => ReactNode;
};

/** Copied from customer-web/components/ui/FormField.tsx (Bölüm 14/19.1: shared
 * token/component approach). */
export default function FormField({ label, hint, error, required, children }: Props) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
        {required ? (
          <span className={styles.required} aria-hidden="true">
            {" "}
            *
          </span>
        ) : null}
      </label>
      {children({ id, "aria-describedby": describedBy, "aria-invalid": Boolean(error) })}
      {hint && !error ? (
        <p id={hintId} className={styles.hint}>
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className={styles.error} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
