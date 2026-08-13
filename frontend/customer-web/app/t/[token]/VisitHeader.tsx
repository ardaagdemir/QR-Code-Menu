import type { TableVisit } from "@/lib/api";
import styles from "./VisitHeader.module.css";

/** Şube + masa bilgisinin müşteriye gösterilmesi (docs/product-requirements.md Bölüm 7) -
 * sahte/yanlış QR'a karşı sağduyu kontrolü. Gap-analysis #17: aynı satırda ziyaretçi
 * sayısı girişi/değiştirme kontrolü - doldurulduysa değer, doldurulmadıysa davet. */
export default function VisitHeader({ visit, onEditGuestCount }: { visit: TableVisit; onEditGuestCount: () => void }) {
  return (
    <header className={styles.header}>
      <h1 className={styles.business}>{visit.businessName}</h1>
      <p className={styles.meta}>
        {visit.branchName} · {visit.tableLabel}
      </p>
      <button type="button" className={styles.guestCountButton} onClick={onEditGuestCount}>
        {visit.guestCount ? `${visit.guestCount} kişi · Değiştir` : "Kaç kişisiniz? Ekle"}
      </button>
      <span className={styles.tideLine} aria-hidden="true" />
    </header>
  );
}
