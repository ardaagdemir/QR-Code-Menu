"use client";

import { useEffect } from "react";
import type { ReactNode } from "react";
import IconButton from "./IconButton";
import styles from "./Dialog.module.css";

type Size = "sm" | "md" | "lg";

type Props = {
  onClose: () => void;
  children: ReactNode;
  labelledBy?: string;
  size?: Size;
};

/** Copied from customer-web/components/ui/Dialog.tsx (Bölüm 14/19.1: shared
 * token/component approach). */
export default function Dialog({ onClose, children, labelledBy, size = "md" }: Props) {
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onClose();
      }
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div
        className={[styles.dialog, styles[size]].join(" ")}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        onClick={(event) => event.stopPropagation()}
      >
        <IconButton aria-label="Kapat" size="sm" className={styles.close} onClick={onClose}>
          ×
        </IconButton>
        {children}
      </div>
    </div>
  );
}
