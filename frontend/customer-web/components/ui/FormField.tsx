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

/** Wraps a label + hint/error around a form control (Input/Textarea/Select), wiring
 * htmlFor/aria-describedby/aria-invalid so every field gets the same accessibility
 * contract without repeating id bookkeeping at each call site (Bölüm 19.1: "form
 * label/error ilişkileri"). Pass the control as a render prop so it receives the
 * generated id/aria props: `<FormField label="Ad">{(p) => <Input {...p} />}</FormField>`. */
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
