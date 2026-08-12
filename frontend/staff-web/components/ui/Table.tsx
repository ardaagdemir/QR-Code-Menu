import type { ReactNode } from "react";
import styles from "./Table.module.css";

type Props = {
  children: ReactNode;
};

/**
 * Bölüm 19.3 "Admin / CRUD ekranları": ortak Table pattern'i. Gerçek
 * `<table>` semantiği kullanılır (erişilebilirlik/screen-reader için
 * `admin.module.css`'in eski div-row listesinden daha doğru); `<thead>`/
 * `<tbody>`/`<th>`/`<td>` çağıran sayfa tarafından yazılır, bu component
 * yalnızca yatay taşmayı `overflow-x: auto` ile kapsayan bir wrapper +
 * ortak hücre/başlık stilini sağlar (Bölüm 19.4: ana flow'da sayfa
 * genelinde yatay overflow yasak, tablo kendi içinde scroll eder).
 */
export default function Table({ children }: Props) {
  return (
    <div className={styles.wrap}>
      <table className={styles.table}>{children}</table>
    </div>
  );
}
