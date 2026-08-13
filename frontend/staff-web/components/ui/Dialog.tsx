"use client";

import { useEffect } from "react";
import type { ReactNode } from "react";
import IconButton from "./IconButton";
import styles from "./Dialog.module.css";

type Size = "sm" | "md" | "lg";
type Tone = "default" | "danger";

type Props = {
  onClose: () => void;
  children: ReactNode;
  labelledBy?: string;
  size?: Size;
  tone?: Tone;
};

/** Copied from customer-web/components/ui/Dialog.tsx (Bölüm 14/19.1: shared
 * token/component approach). `tone="danger"` (ConfirmDialog'un geri dönüşü olmayan
 * aksiyonları için) üst kenara kırmızı "gate stripe" verir - Adım 5 Tasarım Yenilemesi. */
export default function Dialog({ onClose, children, labelledBy, size = "md", tone = "default" }: Props) {
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
        className={[styles.dialog, styles[size], tone === "danger" ? styles.danger : null]
          .filter(Boolean)
          .join(" ")}
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
