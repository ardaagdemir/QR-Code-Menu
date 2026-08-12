"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ApiError, buildOrderTrackingStreamUrl, formatPriceMinorUnits, getOrderTracking, type OrderTracking } from "@/lib/api";
import ErrorState from "@/components/ui/ErrorState";
import Skeleton from "@/components/ui/Skeleton";
import OrderStatusTimeline from "./OrderStatusTimeline";
import styles from "./page.module.css";

/** Gap-analysis #6 (Section 9): "refund başlatıldı / tamamlandı / başarısız" durumları. */
const REFUND_STATUS_MESSAGES: Record<string, string> = {
  REQUESTED: "İadeniz alındı, işleme konuyor.",
  PROCESSING: "İadeniz işleniyor.",
  COMPLETED: "İadeniz tamamlandı.",
  FAILED: "İade işlemi başarısız oldu - lütfen işletmeyle iletişime geçin.",
};

const DELIVERY_MODEL_MESSAGES: Record<string, Record<string, string>> = {
  CUSTOMER_PICKUP: {
    READY: "Sipariş numaranız pickup ekranında - almaya gelebilirsiniz.",
    default: "Hazır olduğunda sipariş numaranız pickup ekranında görünecek.",
  },
  WAITER_DELIVERY: {
    READY: "Siparişiniz hazır, masanıza getiriliyor.",
    default: "Hazır olduğunda siparişiniz masanıza getirilecek.",
  },
};

const ITEM_STATUS_LABELS: Record<string, string> = {
  PENDING_REVIEW: "Onay bekliyor",
  PREPARING: "Hazırlanıyor",
  REJECTED: "Reddedildi",
  READY: "Hazır",
  SERVED: "Teslim edildi",
};

type LoadState = { status: "loading" } | { status: "error"; message: string } | { status: "ready"; tracking: OrderTracking };

/**
 * Section 4, customer-web screen #7: cihaz/cookie bağımsız sipariş takip sayfası - the
 * orderTrackingToken in the URL is the only credential, no qrmenu_session cookie
 * required (Section 5). Live updates via SSE (Section 2), same "refetch on any event"
 * approach as the KDS board rather than applying deltas client-side.
 */
export default function OrderTrackingPage() {
  const params = useParams<{ token: string }>();
  const token = params.token;
  const [state, setState] = useState<LoadState>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    // Two SSE "order-status" events can arrive in quick succession (e.g. an item
    // moving PENDING_REVIEW -> PREPARING immediately followed by -> READY), each
    // starting its own fetch - nothing guarantees those requests resolve in the order
    // they were sent. requestId tags every fetch so a late-arriving response for an
    // already-superseded request is discarded instead of overwriting newer state with
    // stale data.
    let latestRequestId = 0;

    async function load() {
      const requestId = ++latestRequestId;
      try {
        const tracking = await getOrderTracking(token);
        if (!cancelled && requestId === latestRequestId) {
          setState({ status: "ready", tracking });
        }
      } catch (error) {
        if (cancelled || requestId !== latestRequestId) {
          return;
        }
        const message =
          error instanceof ApiError && error.status === 404
            ? "Bu takip linki geçersiz görünüyor."
            : "Sipariş durumu yüklenirken bir sorun oluştu.";
        setState({ status: "error", message });
      }
    }

    void load();

    const eventSource = new EventSource(buildOrderTrackingStreamUrl(token));
    eventSource.addEventListener("order-status", () => load());

    return () => {
      cancelled = true;
      eventSource.close();
    };
  }, [token]);

  if (state.status === "loading") {
    return (
      <main className={styles.page}>
        <div className={styles.header}>
          <Skeleton width="140px" height="2rem" className={styles.skeletonBlock} />
        </div>
        <Skeleton height="3rem" className={styles.skeletonBlock} />
        <Skeleton height="3rem" className={styles.skeletonBlock} />
      </main>
    );
  }

  if (state.status === "error") {
    return (
      <main className={styles.page}>
        <div className={styles.centeredState}>
          <ErrorState title="Sipariş bulunamadı" message={state.message} />
        </div>
      </main>
    );
  }

  const { tracking } = state;

  return (
    <main className={styles.page}>
      <div className={styles.header}>
        <p className={styles.orderNumber}>{tracking.orderNumber !== null ? `Sipariş No: #${tracking.orderNumber}` : "Siparişiniz"}</p>
        <p className={styles.total}>{formatPriceMinorUnits(tracking.totalMinorUnits)}</p>
      </div>

      <OrderStatusTimeline status={tracking.status} />

      {tracking.status === "IN_KITCHEN" || tracking.status === "READY" ? (
        <p className={styles.deliveryNote}>
          {DELIVERY_MODEL_MESSAGES[tracking.deliveryModel]?.[tracking.status] ??
            DELIVERY_MODEL_MESSAGES[tracking.deliveryModel]?.default}
        </p>
      ) : null}
      {tracking.latestRefundStatus ? (
        <p className={styles.deliveryNote}>{REFUND_STATUS_MESSAGES[tracking.latestRefundStatus] ?? tracking.latestRefundStatus}</p>
      ) : tracking.status === "REJECTED_BY_STORE" ? (
        <p className={styles.deliveryNote}>Ödemeniz iade edilecek.</p>
      ) : null}

      <div className={styles.itemList}>
        {tracking.items.map((item, index) => (
          <div key={index} className={styles.item}>
            <span className={styles.itemName}>
              {item.orderedQuantity}× {item.productName}
            </span>
            <span className={styles.itemStatus}>{ITEM_STATUS_LABELS[item.status] ?? item.status}</span>
          </div>
        ))}
      </div>

      <Link href={`/order/track/${token}/receipt`} className={styles.receiptLink}>
        Makbuzu Görüntüle
      </Link>
    </main>
  );
}
