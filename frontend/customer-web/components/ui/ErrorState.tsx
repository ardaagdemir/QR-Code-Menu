"use client";

import Button from "./Button";
import styles from "./ErrorState.module.css";

type Props = {
  title?: string;
  message: string;
  onRetry?: () => void;
};

/** Every failed fetch in the app should surface through this - always a clear message
 * plus a way back in, never a silent dead end (Bölüm 14: "retry ile kurtarma yolu"). */
export default function ErrorState({ title = "Bir şeyler ters gitti", message, onRetry }: Props) {
  return (
    <div className={styles.container} role="alert">
      <p className={styles.title}>{title}</p>
      <p className={styles.message}>{message}</p>
      {onRetry ? (
        <Button variant="secondary" onClick={onRetry}>
          Tekrar Dene
        </Button>
      ) : null}
    </div>
  );
}
