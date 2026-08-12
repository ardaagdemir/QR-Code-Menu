"use client";

import { createContext, useCallback, useContext, useRef, useState } from "react";
import type { ReactNode } from "react";
import styles from "./ToastProvider.module.css";

type ToastTone = "success" | "error" | "info";

type ToastItem = { id: number; tone: ToastTone; message: string };

type ToastContextValue = {
  showToast: (message: string, tone?: ToastTone) => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);

const AUTO_DISMISS_MS = 4000;

/** Toast/feedback provider - mount once near the app root (layout.tsx). Replaces native
 * alert() for success/error/info feedback (Bölüm 19.1: "native alert/confirm yerine
 * ürün içi dialog/toast pattern'i"). */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(0);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const showToast = useCallback(
    (message: string, tone: ToastTone = "info") => {
      const id = nextId.current++;
      setToasts((current) => [...current, { id, tone, message }]);
      setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
    },
    [dismiss],
  );

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div className={styles.viewport} role="status" aria-live="polite">
        {toasts.map((toast) => (
          <div key={toast.id} className={[styles.toast, styles[toast.tone]].join(" ")}>
            {toast.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

/** `const { showToast } = useToast(); showToast("Kaydedildi", "success");` Must be
 * called from a component mounted under ToastProvider. */
export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used within a ToastProvider");
  }
  return context;
}
