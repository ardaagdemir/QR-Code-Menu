import type { TableVisit } from "@/lib/api";
import styles from "./VisitHeader.module.css";

/** Şube + masa bilgisinin müşteriye gösterilmesi (docs/product-requirements.md Bölüm 7) -
 * sahte/yanlış QR'a karşı sağduyu kontrolü. QR karşılama sonrası ilk görülen ekran -
 * referans tasarımın "güçlü hero" yapısı: sıcak degrade zemin, ortalanmış işletme kimliği,
 * "Oturum aktif" durumu. Gap-analysis #17'nin ziyaretçi sayısı kontrolü artık sol üstteki
 * ikon-buton (referanstaki hamburger menünün konumu) - işlevsiz bir dekor yerine gerçek,
 * mevcut bir aksiyon. "Siparişlerim" sağ üstte her zaman görünür (sepet adedi rozeti
 * kullanılmaz) - henüz sipariş yoksa tıklanınca bilgilendirici bir toast gösterilir,
 * mevcut tracking akışı değişmez. */
export default function VisitHeader({
  visit,
  onEditGuestCount,
  onOpenTracking,
}: {
  visit: TableVisit;
  onEditGuestCount: () => void;
  onOpenTracking: () => void;
}) {
  return (
    <header className={styles.header}>
      <div className={styles.topRow}>
        <button
          type="button"
          className={styles.guestCountButton}
          onClick={onEditGuestCount}
          aria-label={visit.guestCount ? `${visit.guestCount} kişi, değiştirmek için dokun` : "Kaç kişi olduğunuzu ekleyin"}
        >
          <GuestIcon />
          <span className={styles.guestCountLabel}>{visit.guestCount ? `${visit.guestCount} kişi` : "Kişi ekle"}</span>
        </button>
        <button type="button" className={styles.trackingButton} onClick={onOpenTracking}>
          <BagIcon />
          Siparişlerim
        </button>
      </div>
      <div className={styles.identity}>
        <h1 className={styles.business}>{visit.businessName}</h1>
        <p className={styles.meta}>
          {visit.branchName} · {visit.tableLabel}
        </p>
        <span className={styles.statusPill}>
          <span className={styles.statusDot} aria-hidden="true" />
          Oturum aktif
        </span>
      </div>
    </header>
  );
}

function GuestIcon() {
  return (
    <svg width={18} height={18} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} aria-hidden="true">
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20c0-3.87 3.13-7 7-7s7 3.13 7 7" strokeLinecap="round" />
    </svg>
  );
}

function BagIcon() {
  return (
    <svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} aria-hidden="true">
      <path d="M6 8h12l-1 12H7L6 8Z" strokeLinejoin="round" />
      <path d="M9 8V6a3 3 0 0 1 6 0v2" strokeLinecap="round" />
    </svg>
  );
}
