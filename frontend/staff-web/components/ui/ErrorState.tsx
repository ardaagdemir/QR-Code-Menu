"use client";

import Button from "./Button";
import styles from "./ErrorState.module.css";

type Props = {
  title?: string;
  message: string;
  onRetry?: () => void;
};

/** Copied from customer-web/components/ui/ErrorState.tsx (Bölüm 14/19.1: shared
 * token/component approach). */
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
