import type { ReactNode } from "react";
import styles from "./PageHeader.module.css";

type Props = {
  title: string;
  description?: string;
  actions?: ReactNode;
};

/**
 * Bölüm 19.3 "Admin / CRUD ekranları": "sayfa başlığında title + açıklama +
 * primary action pattern'i kullanılır." `actions` genelde tek bir primary
 * `Button` (ör. "+ Ekle") veya ilgili sayfaya dönen bir `Link` alır.
 */
export default function PageHeader({ title, description, actions }: Props) {
  return (
    <div className={styles.header}>
      <div className={styles.text}>
        <h1 className={styles.title}>{title}</h1>
        {description ? <p className={styles.description}>{description}</p> : null}
      </div>
      {actions ? <div className={styles.actions}>{actions}</div> : null}
    </div>
  );
}
