"use client";

import { useRef, useState, useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  ApiError,
  acceptOrder,
  buildKitchenStreamUrl,
  formatPriceMinorUnits,
  getPendingAcceptanceOrders,
  rejectOrder,
  type OrderControlOrder,
} from "@/lib/api";
import StaffNav from "@/components/layout/StaffNav";
import Button from "@/components/ui/Button";
import styles from "./page.module.css";

const REJECT_REASONS: { value: string; label: string }[] = [
  { value: "OUT_OF_STOCK", label: "Ürün tükendi" },
  { value: "KITCHEN_BUSY", label: "Mutfak yoğun" },
  { value: "CLOSED", label: "Şube kapanıyor/kapalı" },
  { value: "OTHER", label: "Diğer" },
];

/**
 * Gap-analysis #2: kasa dashboard (Section 10.1) - ödemesi başarılı, işletme onayı
 * bekleyen siparişleri gösterir; kasa her siparişi tümüyle KABUL (-> mutfak) veya
 * RED (-> otomatik tam iade) eder. Aynı "SSE'yi salt refetch sinyali olarak kullan"
 * deseni KDS/pickup board ile paylaşılıyor - branş-kitchen kanalı zaten her sipariş
 * durum değişikliğinde (AWAITING_STORE_ACCEPTANCE'a düşüş dahil) event yayınlıyor.
 */
export default function CashierDashboardPage() {
  const params = useParams<{ branchId: string }>();
  const branchId = params.branchId;
  const router = useRouter();

  const [orders, setOrders] = useState<OrderControlOrder[]>([]);
  const [connectionStatus, setConnectionStatus] = useState<"connecting" | "live" | "reconnecting">("connecting");
  const [error, setError] = useState<string | null>(null);
  const [pendingOrderId, setPendingOrderId] = useState<string | null>(null);
  const [rejectingOrderId, setRejectingOrderId] = useState<string | null>(null);
  const reasonCodeRef = useRef<Record<string, string>>({});
  const noteRef = useRef<Record<string, string>>({});
  const latestRequestIdRef = useRef(0);

  async function reloadOrders() {
    const requestId = ++latestRequestIdRef.current;
    try {
      const data = await getPendingAcceptanceOrders(branchId);
      if (requestId === latestRequestIdRef.current) {
        setOrders(data);
        setError(null);
      }
    } catch (err) {
      if (requestId !== latestRequestIdRef.current) {
        return;
      }
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        router.replace("/");
        return;
      }
      setError("Sipariş listesi yüklenemedi.");
    }
  }

  useEffect(() => {
    let cancelled = false;

    async function fetchOrders() {
      const requestId = ++latestRequestIdRef.current;
      try {
        const data = await getPendingAcceptanceOrders(branchId);
        if (!cancelled && requestId === latestRequestIdRef.current) {
          setOrders(data);
          setError(null);
        }
      } catch (err) {
        if (cancelled || requestId !== latestRequestIdRef.current) {
          return;
        }
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          router.replace("/");
          return;
        }
        setError("Sipariş listesi yüklenemedi.");
      }
    }

    const eventSource = new EventSource(buildKitchenStreamUrl(branchId), { withCredentials: true });
    eventSource.addEventListener("open", () => setConnectionStatus("live"));
    eventSource.addEventListener("error", () => setConnectionStatus("reconnecting"));
    eventSource.addEventListener("order-status", () => fetchOrders());
    void fetchOrders();

    return () => {
      cancelled = true;
      eventSource.close();
    };
  }, [branchId, router]);

  async function handleAccept(orderId: string) {
    setPendingOrderId(orderId);
    setError(null);
    try {
      await acceptOrder(branchId, orderId);
      await reloadOrders();
    } catch {
      setError("Sipariş kabul edilemedi.");
    } finally {
      setPendingOrderId(null);
    }
  }

  async function handleSubmitReject(orderId: string) {
    const reasonCode = reasonCodeRef.current[orderId] ?? REJECT_REASONS[0].value;
    const note = noteRef.current[orderId] ?? "";
    setPendingOrderId(orderId);
    setError(null);
    try {
      await rejectOrder(branchId, orderId, reasonCode, note);
      setRejectingOrderId(null);
      await reloadOrders();
    } catch {
      setError("Sipariş reddedilemedi.");
    } finally {
      setPendingOrderId(null);
    }
  }

  return (
    <>
      <StaffNav />
      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>Kasa</h1>
          <div className={styles.headerActions}>
            <Link href={`/kitchen/${branchId}`} className={styles.navLink}>
              Mutfak
            </Link>
            <Link href={`/refunds/${branchId}`} className={styles.navLink}>
              İadeler
            </Link>
            <span className={styles.connectionStatus}>
              {connectionStatus === "live" ? "Canlı" : connectionStatus === "reconnecting" ? "Yeniden bağlanıyor…" : "Bağlanıyor…"}
            </span>
          </div>
        </div>

        {error ? <p className={styles.error}>{error}</p> : null}

        {orders.length === 0 ? (
          <p className={styles.empty}>Onay bekleyen sipariş yok.</p>
        ) : (
          <div className={styles.grid}>
            {orders.map((order) => (
              <article key={order.orderId} className={styles.card}>
                <div className={styles.cardHeader}>
                  <span className={styles.orderNumber}>#{order.orderNumber ?? "—"}</span>
                  <span className={styles.orderTotal}>{formatPriceMinorUnits(order.totalMinorUnits)}</span>
                </div>

                {order.items.map((item) => (
                  <div key={item.id} className={styles.item}>
                    {item.orderedQuantity}× {item.productName}
                    {item.options.length > 0 ? (
                      <div className={styles.itemOptions}>{item.options.map((option) => option.name).join(", ")}</div>
                    ) : null}
                  </div>
                ))}

                {rejectingOrderId === order.orderId ? (
                  <div className={styles.rejectForm}>
                    <select
                      className={styles.select}
                      defaultValue={REJECT_REASONS[0].value}
                      onChange={(event) => {
                        reasonCodeRef.current[order.orderId] = event.target.value;
                      }}
                      aria-label="Red nedeni"
                    >
                      {REJECT_REASONS.map((reason) => (
                        <option key={reason.value} value={reason.value}>
                          {reason.label}
                        </option>
                      ))}
                    </select>
                    <input
                      className={styles.noteInput}
                      placeholder="Not (opsiyonel)"
                      onChange={(event) => {
                        noteRef.current[order.orderId] = event.target.value;
                      }}
                      aria-label="Red notu"
                    />
                    <div className={styles.cardActions}>
                      <Button
                        size="md"
                        variant="secondary"
                        disabled={pendingOrderId === order.orderId}
                        onClick={() => handleSubmitReject(order.orderId)}
                      >
                        {pendingOrderId === order.orderId ? "İşleniyor…" : "Reddi Onayla"}
                      </Button>
                      <Button size="md" variant="ghost" onClick={() => setRejectingOrderId(null)}>
                        Vazgeç
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className={styles.cardActions}>
                    <Button size="md" disabled={pendingOrderId === order.orderId} onClick={() => handleAccept(order.orderId)}>
                      Kabul Et
                    </Button>
                    <Button
                      size="md"
                      variant="secondary"
                      disabled={pendingOrderId === order.orderId}
                      onClick={() => setRejectingOrderId(order.orderId)}
                    >
                      Reddet
                    </Button>
                  </div>
                )}
              </article>
            ))}
          </div>
        )}
      </main>
    </>
  );
}
