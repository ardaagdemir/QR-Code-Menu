import type { ReactNode } from "react";
import styles from "./EmptyState.module.css";

type Props = {
  icon?: ReactNode;
  title: string;
  description?: string;
};

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
