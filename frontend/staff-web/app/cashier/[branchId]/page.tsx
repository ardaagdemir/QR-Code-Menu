"use client";

import { useEffect, useRef, useState } from "react";
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
import { formatElapsedMinutes, waitingUrgency } from "@/lib/time";
import { playCriticalOrderAlert } from "@/lib/alertSound";
import AppShell from "@/components/layout/AppShell";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import Select from "@/components/ui/Select";
import Textarea from "@/components/ui/Textarea";
import styles from "./page.module.css";

const REJECT_REASONS: { value: string; label: string }[] = [
  { value: "OUT_OF_STOCK", label: "Ürün tükendi" },
  { value: "KITCHEN_BUSY", label: "Mutfak yoğun" },
  { value: "CLOSED", label: "Şube kapanıyor/kapalı" },
  { value: "OTHER", label: "Diğer" },
];

const WAITING_BADGE_TONE = { normal: "neutral", warning: "warning", danger: "danger" } as const;

/**
 * Gap-analysis #2: kasa dashboard (Section 10.1) - ödemesi başarılı, işletme onayı
 * bekleyen siparişleri gösterir; kasa her siparişi tümüyle KABUL (-> mutfak) veya
 * RED (-> otomatik tam iade) eder. Aynı "SSE'yi salt refetch sinyali olarak kullan"
 * deseni KDS/pickup board ile paylaşılıyor - branş-kitchen kanalı zaten her sipariş
 * durum değişikliğinde (AWAITING_STORE_ACCEPTANCE'a düşüş dahil) event yayınlıyor.
 *
 * Bölüm 19.3 "Kasa": kart masa/sipariş no/ödeme durumu/bekleme süresi/tutar/ürün
 * özetini ilk bakışta vermeli. "Ödeme Alındı" rozeti statik - bu liste yalnızca
 * CustomerOrder.markAwaitingStoreAcceptance()'ın garanti ettiği gibi doğrulanmış
 * ödemesi olan siparişleri döndürüyor, ayrı bir API alanı gerekmiyor.
 */
export default function CashierDashboardPage() {
  const params = useParams<{ branchId: string }>();
  const branchId = params.branchId;
  const router = useRouter();

  const [orders, setOrders] = useState<OrderControlOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [connectionStatus, setConnectionStatus] = useState<"connecting" | "live" | "reconnecting">("connecting");
  const [error, setError] = useState<string | null>(null);
  const [pendingOrderId, setPendingOrderId] = useState<string | null>(null);
  const [rejectingOrderId, setRejectingOrderId] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const reasonCodeRef = useRef<Record<string, string>>({});
  const noteRef = useRef<Record<string, string>>({});
  const latestRequestIdRef = useRef(0);
  // Section 6: kritik alarmı sipariş başına yalnızca bir kez çalar - her 15sn'lik "now"
  // tazelemesinde tekrar tekrar öttürmemek için hangi siparişler için zaten uyarıldığını tutar.
  const alertedOrderIdsRef = useRef<Set<string>>(new Set());

  // Bekleme süresi rozetlerini yalnızca görsel olarak tazeler - yeniden fetch tetiklemez.
  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 15000);
    return () => clearInterval(interval);
  }, []);

  // Section 6: "Mümkünse sesli/görsel uyarı verilsin" - bir sipariş kritik eşiği
  // geçtiğinde (branch'in configurable storeAcceptanceTimeoutSeconds'ı) bir kez sesli uyarı çalar.
  useEffect(() => {
    const stillOpenOrderIds = new Set(orders.map((order) => order.orderId));
    for (const orderId of alertedOrderIdsRef.current) {
      if (!stillOpenOrderIds.has(orderId)) {
        alertedOrderIdsRef.current.delete(orderId);
      }
    }
    for (const order of orders) {
      const urgency = waitingUrgency(order.statusSince, now, order.storeAcceptanceTimeoutSeconds);
      if (urgency === "danger" && !alertedOrderIdsRef.current.has(order.orderId)) {
        alertedOrderIdsRef.current.add(order.orderId);
        playCriticalOrderAlert();
      }
    }
  }, [orders, now]);

  async function reloadOrders() {
    const requestId = ++latestRequestIdRef.current;
    try {
      const data = await getPendingAcceptanceOrders(branchId);
      if (requestId === latestRequestIdRef.current) {
        setOrders(data);
        setError(null);
        setLoading(false);
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
      setLoading(false);
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
          setLoading(false);
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
        setLoading(false);
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
    <AppShell>
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
              <span className={[styles.connectionDot, styles[`connectionDot--${connectionStatus}`]].join(" ")} aria-hidden="true" />
              {connectionStatus === "live" ? "Canlı" : connectionStatus === "reconnecting" ? "Yeniden bağlanıyor…" : "Bağlanıyor…"}
            </span>
          </div>
        </div>

        {error ? <ErrorState message={error} onRetry={reloadOrders} /> : null}

        {loading ? (
          <p className={styles.loading}>Yükleniyor…</p>
        ) : orders.length === 0 ? (
          <EmptyState title="Onay bekleyen sipariş yok" description="Ödemesi tamamlanan yeni siparişler burada görünecek." />
        ) : (
          <div className={styles.grid}>
            {orders.map((order) => {
              const urgency = waitingUrgency(order.statusSince, now, order.storeAcceptanceTimeoutSeconds);
              return (
                <article
                  key={order.orderId}
                  className={[styles.card, styles[`card--${urgency}`], urgency === "danger" ? styles["card--critical"] : ""].join(" ")}
                >
                  <div className={styles.cardHeader}>
                    <div className={styles.cardHeaderMain}>
                      <span className={styles.tableLabel}>{order.tableLabel ?? "Masa —"}</span>
                      <span className={styles.orderNumber}>#{order.orderNumber ?? "—"}</span>
                    </div>
                    <span className={styles.orderTotal}>{formatPriceMinorUnits(order.totalMinorUnits)}</span>
                  </div>

                  <div className={styles.cardMeta}>
                    <Badge tone="success">Ödeme Alındı</Badge>
                    <Badge tone={WAITING_BADGE_TONE[urgency]}>
                      {urgency === "danger" ? "Kritik · " : ""}
                      {formatElapsedMinutes(order.statusSince, now)} bekliyor
                    </Badge>
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
                      <Select
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
                      </Select>
                      <Textarea
                        rows={2}
                        placeholder="Not (opsiyonel)"
                        onChange={(event) => {
                          noteRef.current[order.orderId] = event.target.value;
                        }}
                        aria-label="Red notu"
                      />
                      <div className={styles.cardActions}>
                        <Button
                          variant="danger"
                          disabled={pendingOrderId === order.orderId}
                          onClick={() => handleSubmitReject(order.orderId)}
                        >
                          {pendingOrderId === order.orderId ? "İşleniyor…" : "Reddi Onayla"}
                        </Button>
                        <Button variant="ghost" onClick={() => setRejectingOrderId(null)}>
                          Vazgeç
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <div className={styles.cardActions}>
                      <Button disabled={pendingOrderId === order.orderId} onClick={() => handleAccept(order.orderId)}>
                        Kabul Et
                      </Button>
                      <Button
                        variant="secondary"
                        disabled={pendingOrderId === order.orderId}
                        onClick={() => setRejectingOrderId(order.orderId)}
                      >
                        Reddet
                      </Button>
                    </div>
                  )}
                </article>
              );
            })}
          </div>
        )}
      </main>
    </AppShell>
  );
}
