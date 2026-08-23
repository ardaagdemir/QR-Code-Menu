"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, formatPriceMinorUnits, getOrderTracking, type OrderTracking } from "@/lib/api";
import BottomSheet from "@/components/ui/BottomSheet";
import EmptyState from "@/components/ui/EmptyState";
import IconButton from "@/components/ui/IconButton";
import type { OrderHistoryEntry } from "./orderHistoryStorage";
import styles from "./OrdersSheet.module.css";

type StatusVariant = "neutral" | "warning" | "success" | "danger";

type StatusMeta = {
  label: string;
  variant: StatusVariant;
  /** Henüz sonuçlanmamış (tamamlanmadı/iptal/reddedilmedi) siparişler listede daha
   * belirgin gösterilir - müşterinin takip etmesi gereken siparişler bunlar. */
  active: boolean;
};

const ORDER_STATUS_META: Record<string, StatusMeta> = {
  DRAFT: { label: "Sepette", variant: "neutral", active: true },
  AWAITING_PAYMENT: { label: "Ödeme bekleniyor", variant: "warning", active: true },
  PAYMENT_FAILED: { label: "Ödeme başarısız", variant: "danger", active: false },
  AWAITING_STORE_ACCEPTANCE: { label: "İşletme onayı bekleniyor", variant: "warning", active: true },
  IN_KITCHEN: { label: "Hazırlanıyor", variant: "warning", active: true },
  READY: { label: "Hazır", variant: "success", active: true },
  COMPLETED: { label: "Tamamlandı", variant: "neutral", active: false },
  REJECTED_BY_STORE: { label: "Reddedildi", variant: "danger", active: false },
  CANCELLED: { label: "İptal edildi", variant: "neutral", active: false },
};

function statusMeta(status: string): StatusMeta {
  return ORDER_STATUS_META[status] ?? { label: status, variant: "neutral", active: false };
}

/** Gap-analysis #6: aynı ekranda hem sipariş durumu hem iade durumu - order.status
 * REJECTED_BY_STORE olsa bile "iade tamamlandı" yalnızca refund.status COMPLETED
 * olduğunda söylenir, /order/track sayfasındaki REFUND_STATUS_MESSAGES ile aynı kural. */
const REFUND_STATUS_META: Record<string, { label: string; variant: StatusVariant }> = {
  REQUESTED: { label: "İade işleme alınıyor", variant: "neutral" },
  PROCESSING: { label: "İade işleniyor", variant: "warning" },
  COMPLETED: { label: "İade tamamlandı", variant: "success" },
  FAILED: { label: "İade başarısız", variant: "danger" },
};

function refundStatusMeta(status: string): { label: string; variant: StatusVariant } {
  return REFUND_STATUS_META[status] ?? { label: status, variant: "neutral" };
}

const dateFormatter = new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

function formatAddedAt(addedAt: string | null): string | null {
  if (!addedAt) {
    return null;
  }
  const date = new Date(addedAt);
  return Number.isNaN(date.getTime()) ? null : dateFormatter.format(date);
}

type LoadedEntry = { token: string; addedAt: string | null; tracking: OrderTracking | null };

type Props = {
  entries: OrderHistoryEntry[];
  onPrune: (token: string) => void;
  onClose: () => void;
};

/**
 * "Siparişlerim" - kalıcı (localStorage, masa bazlı) orderTrackingToken listesindeki
 * her sipariş için backend'den taze durum çekilir (ham veri hiç saklanmaz). Artık
 * bulunamayan (404) token'lar sessizce listeden ve kalıcı depodan temizlenir; geçici
 * ağ hatalarında token korunur, satır "yüklenemedi" olarak gösterilir - tek bir başarısız
 * istek yüzünden geçmiş sipariş kaybolmaz.
 */
export default function OrdersSheet({ entries, onPrune, onClose }: Props) {
  const router = useRouter();
  const tokens = entries.map((entry) => entry.token);
  const tokensKey = tokens.join(",");
  // useFavorites.ts'deki "prop değişince render sırasında state ayarla" deseni: entries
  // (dolayısıyla açık siparişler) değiştiğinde loaded'ı senkron olarak sıfırlar - efekt
  // içinde setState çağırmak yerine (bkz. react-hooks/set-state-in-effect).
  const [trackedTokensKey, setTrackedTokensKey] = useState(tokensKey);
  const [loaded, setLoaded] = useState<LoadedEntry[] | null>(null);

  if (tokensKey !== trackedTokensKey) {
    setTrackedTokensKey(tokensKey);
    setLoaded(null);
  }

  useEffect(() => {
    if (entries.length === 0) {
      return;
    }
    let cancelled = false;

    async function load() {
      const results = await Promise.all(
        entries.map(async ({ token, addedAt }): Promise<LoadedEntry | null> => {
          try {
            const tracking = await getOrderTracking(token);
            return { token, addedAt, tracking };
          } catch (error) {
            if (error instanceof ApiError && error.status === 404) {
              onPrune(token);
              return null;
            }
            return { token, addedAt, tracking: null };
          }
        }),
      );
      if (!cancelled) {
        setLoaded(results.filter((entry): entry is LoadedEntry => entry !== null).reverse());
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- entries compared by token content, not identity
  }, [tokensKey]);

  return (
    <BottomSheet onClose={onClose} labelledBy="orders-sheet-title">
      <div className={styles.header}>
        <h2 id="orders-sheet-title" className={styles.title}>
          Siparişlerim
        </h2>
        <IconButton aria-label="Kapat" size="sm" onClick={onClose}>
          ×
        </IconButton>
      </div>

      {entries.length === 0 ? (
        <EmptyState title="Henüz siparişiniz yok" description="Sipariş verdiğinizde burada listelenecek." />
      ) : loaded === null ? (
        <p className={styles.status}>Yükleniyor…</p>
      ) : loaded.length === 0 ? (
        <EmptyState title="Henüz siparişiniz yok" description="Sipariş verdiğinizde burada listelenecek." />
      ) : (
        <ul className={styles.list}>
          {loaded.map((entry) => {
            if (!entry.tracking) {
              return (
                <li key={entry.token} className={`${styles.card} ${styles.cardError}`}>
                  <span className={styles.itemError}>Sipariş yüklenemedi.</span>
                </li>
              );
            }

            const meta = statusMeta(entry.tracking.status);
            const dateLabel = formatAddedAt(entry.addedAt);
            const refundMeta = entry.tracking.latestRefundStatus ? refundStatusMeta(entry.tracking.latestRefundStatus) : null;

            return (
              <li key={entry.token}>
                <button
                  type="button"
                  className={`${styles.card} ${meta.active ? styles.cardActive : styles.cardTerminal}`}
                  onClick={() => router.push(`/order/track/${entry.token}`)}
                >
                  <span className={styles.cardMain}>
                    <span className={styles.orderNumber}>
                      {entry.tracking.orderNumber !== null ? `#${entry.tracking.orderNumber}` : "Sipariş"}
                    </span>
                    {dateLabel ? <span className={styles.timestamp}>{dateLabel}</span> : null}
                  </span>
                  <span className={styles.cardRight}>
                    <span className={`${styles.badge} ${styles[`badge_${meta.variant}`]}`}>{meta.label}</span>
                    {refundMeta ? (
                      <span className={`${styles.badge} ${styles[`badge_${refundMeta.variant}`]}`}>{refundMeta.label}</span>
                    ) : null}
                    <span className={styles.amount}>{formatPriceMinorUnits(entry.tracking.totalMinorUnits)}</span>
                  </span>
                  <span className={styles.chevron} aria-hidden="true">
                    ›
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </BottomSheet>
  );
}
