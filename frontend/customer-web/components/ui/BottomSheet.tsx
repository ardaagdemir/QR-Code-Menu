"use client";

import { useEffect } from "react";
import type { ReactNode } from "react";
import styles from "./BottomSheet.module.css";

type Props = {
  onClose: () => void;
  children: ReactNode;
  labelledBy?: string;
};

/** Shared overlay + sheet shell for both the product options sheet and the cart drawer
 * (Bölüm 14: reusable component structure). Handles the accessibility contract every
 * modal/sheet needs: role="dialog", aria-modal, and Escape-to-close. */
export default function BottomSheet({ onClose, children, labelledBy }: Props) {
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
        className={styles.sheet}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        onClick={(event) => event.stopPropagation()}
      >
        {children}
      </div>
    </div>
  );
}
