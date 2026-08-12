import type { ReactNode } from "react";
import styles from "./EmptyState.module.css";

type Props = {
  icon?: ReactNode;
  title: string;
  description?: string;
};

/** Copied from customer-web/components/ui/EmptyState.tsx (Bölüm 14/19.1: shared
 * token/component approach). */
export default function EmptyState({ icon, title, description }: Props) {
  return (
    <div className={styles.container} role="status">
      {icon ? (
        <div className={styles.icon} aria-hidden="true">
          {icon}
        </div>
      ) : null}
      <p className={styles.title}>{title}</p>
      {description ? <p className={styles.description}>{description}</p> : null}
    </div>
  );
}
