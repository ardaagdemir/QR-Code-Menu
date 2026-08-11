import type { TableVisit } from "@/lib/api";
import styles from "./VisitHeader.module.css";

/** Şube + masa bilgisinin müşteriye gösterilmesi (docs/product-requirements.md Bölüm 7) -
 * sahte/yanlış QR'a karşı sağduyu kontrolü. */
export default function VisitHeader({ visit }: { visit: TableVisit }) {
  return (
    <header className={styles.header}>
      <h1 className={styles.business}>{visit.businessName}</h1>
      <p className={styles.meta}>
        {visit.branchName} · {visit.tableLabel}
      </p>
    </header>
  );
}
