"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  BellRing,
  Check,
  ChefHat,
  Loader2,
  Receipt,
  Send,
  TrendingUp,
  Undo2,
  UtensilsCrossed,
  Wallet,
  Timer,
  X,
} from "lucide-react";
import {
  ApiError,
  acceptOrder,
  buildOrderStreamUrl,
  completeOrder,
  formatPriceMinorUnits,
  getInProgressOrders,
  getKitchenFinancialSummary,
  getPendingAcceptanceOrders,
  getReadyOrders,
  markOrderReady,
  me,
  rejectOrder,
  type KitchenFinancialSummary,
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
  { value: "KITCHEN_BUSY", label: "Yoğunluk" },
  { value: "CLOSED", label: "Şube kapanıyor/kapalı" },
  { value: "OTHER", label: "Diğer" },
];

const WAITING_BADGE_TONE = { normal: "neutral", warning: "warning", danger: "danger" } as const;

/**
 * Ürün kararı: ayrı bir Mutfak/KDS ekranı yok - Kasa, sipariş operasyonunun tek ekranı.
 * Üç liste tek sayfada yaşar: onay bekleyen siparişler (kabul/red), kabul edilmiş ve
 * hâlâ hazırlanan siparişler (tek "Hazır" aksiyonuyla PREPARING -> READY, item bazlı bir
 * karar adımı yok) ve hazır/teslim bekleyen siparişler. Üç liste de aynı SSE kanalını
 * (Section 2: "salt refetch sinyali") paylaşır - branch'in order-control kanalı zaten
 * her durum geçişinde event yayınlıyor.
 */
export default function CashierDashboardPage() {
  const params = useParams<{ branchId: string }>();
  const branchId = params.branchId;
  const router = useRouter();

  const [pendingOrders, setPendingOrders] = useState<OrderControlOrder[]>([]);
  const [inProgressOrders, setInProgressOrders] = useState<OrderControlOrder[]>([]);
  const [readyOrders, setReadyOrders] = useState<OrderControlOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [connectionStatus, setConnectionStatus] = useState<"connecting" | "live" | "reconnecting">("connecting");
  const [error, setError] = useState<string | null>(null);
  const [pendingOrderId, setPendingOrderId] = useState<string | null>(null);
  const [rejectingOrderId, setRejectingOrderId] = useState<string | null>(null);
  const [readyingOrderId, setReadyingOrderId] = useState<string | null>(null);
  const [completingOrderId, setCompletingOrderId] = useState<string | null>(null);
  const [financialSummary, setFinancialSummary] = useState<KitchenFinancialSummary | null>(null);
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

  useEffect(() => {
    const stillOpenOrderIds = new Set(pendingOrders.map((order) => order.orderId));
    for (const orderId of alertedOrderIdsRef.current) {
      if (!stillOpenOrderIds.has(orderId)) {
        alertedOrderIdsRef.current.delete(orderId);
      }
    }
    for (const order of pendingOrders) {
      const urgency = waitingUrgency(order.statusSince, now, order.storeAcceptanceTimeoutSeconds);
      if (urgency === "danger" && !alertedOrderIdsRef.current.has(order.orderId)) {
        alertedOrderIdsRef.current.add(order.orderId);
        playCriticalOrderAlert();
      }
    }
  }, [pendingOrders, now]);

  const reloadAll = useCallback(async () => {
    const requestId = ++latestRequestIdRef.current;
    try {
      const [pending, inProgress, ready] = await Promise.all([
        getPendingAcceptanceOrders(branchId),
        getInProgressOrders(branchId),
        getReadyOrders(branchId),
      ]);
      if (requestId === latestRequestIdRef.current) {
        setPendingOrders(pending);
        setInProgressOrders(inProgress);
        setReadyOrders(ready);
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
  }, [branchId, router]);

  useEffect(() => {
    let cancelled = false;

    async function fetchAll() {
      const requestId = ++latestRequestIdRef.current;
      try {
        const [pending, inProgress, ready] = await Promise.all([
          getPendingAcceptanceOrders(branchId),
          getInProgressOrders(branchId),
          getReadyOrders(branchId),
        ]);
        if (!cancelled && requestId === latestRequestIdRef.current) {
          setPendingOrders(pending);
          setInProgressOrders(inProgress);
          setReadyOrders(ready);
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

    const eventSource = new EventSource(buildOrderStreamUrl(branchId), { withCredentials: true });
    eventSource.addEventListener("open", () => setConnectionStatus("live"));
    eventSource.addEventListener("error", () => setConnectionStatus("reconnecting"));
    eventSource.addEventListener("order-status", () => fetchAll());
    void fetchAll();

    return () => {
      cancelled = true;
      eventSource.close();
    };
  }, [branchId, router]);

  useEffect(() => {
    let cancelled = false;
    // Gap-analysis #14: REPORT_FINANCIAL_SUMMARY_VIEW is only granted to
    // BUSINESS_ADMIN/BRANCH_MANAGER (see StaffRole) - checking the role here just
    // avoids a call that would 403 for CASHIER; the backend enforces this regardless.
    me()
      .then((context) => {
        if (cancelled || (context.role !== "BUSINESS_ADMIN" && context.role !== "BRANCH_MANAGER")) {
          return;
        }
        const today = new Date().toISOString().slice(0, 10);
        return getKitchenFinancialSummary(branchId, today, today).then((summary) => {
          if (!cancelled) {
            setFinancialSummary(summary);
          }
        });
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [branchId]);

  async function handleAccept(orderId: string) {
    setPendingOrderId(orderId);
    setError(null);
    try {
      await acceptOrder(branchId, orderId);
      await reloadAll();
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
      await reloadAll();
    } catch {
      setError("Sipariş reddedilemedi.");
    } finally {
      setPendingOrderId(null);
    }
  }

  async function handleMarkReady(orderId: string) {
    setReadyingOrderId(orderId);
    try {
      await markOrderReady(branchId, orderId);
      await reloadAll();
    } catch {
      setError("Sipariş hazır olarak işaretlenemedi.");
    } finally {
      setReadyingOrderId(null);
    }
  }

  async function handleComplete(orderId: string) {
    setCompletingOrderId(orderId);
    try {
      await completeOrder(branchId, orderId);
      await reloadAll();
    } catch {
      setError("Sipariş tamamlanamadı.");
    } finally {
      setCompletingOrderId(null);
    }
  }

  return (
    <AppShell theme="warm">
      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>Kasa</h1>
          <div className={styles.headerActions}>
            <Link href={`/refunds/${branchId}`} className={styles.navLink}>
              <Undo2 size={14} aria-hidden="true" />
              İadeler
            </Link>
            <span className={styles.connectionStatus}>
              <span className={[styles.connectionDot, styles[`connectionDot--${connectionStatus}`]].join(" ")} aria-hidden="true" />
              {connectionStatus === "live" ? "Canlı" : connectionStatus === "reconnecting" ? "Yeniden bağlanıyor…" : "Bağlanıyor…"}
            </span>
          </div>
        </div>

        {error ? <ErrorState message={error} onRetry={reloadAll} /> : null}

        {financialSummary ? (
          <div className={styles.kpiStrip}>
            <div className={styles.kpiCard}>
              <span className={styles.kpiIcon} aria-hidden="true">
                <Wallet size={20} />
              </span>
              <span className={styles.kpiBody}>
                <span className={styles.kpiLabel}>Bugün brüt satış</span>
                <span className={styles.kpiValue}>{formatPriceMinorUnits(financialSummary.grossSalesMinorUnits)}</span>
              </span>
            </div>
            <div className={styles.kpiCard}>
              <span className={styles.kpiIcon} aria-hidden="true">
                <TrendingUp size={20} />
              </span>
              <span className={styles.kpiBody}>
                <span className={styles.kpiLabel}>Net satış</span>
                <span className={styles.kpiValue}>{formatPriceMinorUnits(financialSummary.netSalesMinorUnits)}</span>
              </span>
            </div>
            <div className={styles.kpiCard}>
              <span className={styles.kpiIcon} aria-hidden="true">
                <Receipt size={20} />
              </span>
              <span className={styles.kpiBody}>
                <span className={styles.kpiLabel}>Sipariş sayısı</span>
                <span className={styles.kpiValue}>{financialSummary.orderCount}</span>
              </span>
            </div>
          </div>
        ) : null}

        {loading ? (
          <p className={styles.loading}>
            <Loader2 size={16} className={styles.loadingSpinner} aria-hidden="true" />
            Yükleniyor…
          </p>
        ) : (
          <div className={styles.board}>
            <section className={`${styles.column} ${styles["column--pending"]}`}>
              <header className={styles.columnHeader}>
                <span className={styles.columnIcon} aria-hidden="true">
                  <Timer size={16} />
                </span>
                <h2 className={styles.columnTitle}>Onay Bekleyen</h2>
                <span className={styles.columnCount}>{pendingOrders.length}</span>
              </header>
              {pendingOrders.length === 0 ? (
                <EmptyState
                  icon={<Timer size={28} aria-hidden="true" />}
                  title="Onay bekleyen sipariş yok"
                  description="Ödemesi tamamlanan yeni siparişler burada görünecek."
                />
              ) : (
                <div className={styles.columnBody}>
                  {pendingOrders.map((order) => {
                    const urgency = waitingUrgency(order.statusSince, now, order.storeAcceptanceTimeoutSeconds);
                    return (
                      <article
                        key={order.orderId}
                        className={[styles.card, styles[`card--${urgency}`], urgency === "danger" ? styles["card--critical"] : ""].join(
                          " ",
                        )}
                      >
                        <div className={styles.cardHeader}>
                          <div className={styles.cardHeaderMain}>
                            <span className={styles.tableLabel}>
                              <UtensilsCrossed size={14} className={styles.tableLabelIcon} aria-hidden="true" />
                              {order.tableLabel ?? "Masa —"}
                            </span>
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
                              <Check size={16} aria-hidden="true" />
                              Kabul Et
                            </Button>
                            <Button
                              variant="secondary"
                              disabled={pendingOrderId === order.orderId}
                              onClick={() => setRejectingOrderId(order.orderId)}
                            >
                              <X size={16} aria-hidden="true" />
                              Reddet
                            </Button>
                          </div>
                        )}
                      </article>
                    );
                  })}
                </div>
              )}
            </section>

            <section className={`${styles.column} ${styles["column--preparing"]}`}>
              <header className={styles.columnHeader}>
                <span className={styles.columnIcon} aria-hidden="true">
                  <ChefHat size={16} />
                </span>
                <h2 className={styles.columnTitle}>Hazırlanıyor</h2>
                <span className={styles.columnCount}>{inProgressOrders.length}</span>
              </header>
              {inProgressOrders.length === 0 ? (
                <EmptyState
                  icon={<ChefHat size={28} aria-hidden="true" />}
                  title="Hazırlanan sipariş yok"
                  description="Kabul edilen siparişler burada görünecek."
                />
              ) : (
                <div className={styles.columnBody}>
                  {inProgressOrders.map((order) => {
                    const urgency = waitingUrgency(order.statusSince, now);
                    return (
                      <article key={order.orderId} className={[styles.card, styles[`card--${urgency}`]].join(" ")}>
                        <div className={styles.cardHeader}>
                          <div className={styles.cardHeaderMain}>
                            <span className={styles.tableLabel}>
                              <UtensilsCrossed size={14} className={styles.tableLabelIcon} aria-hidden="true" />
                              {order.tableLabel ?? "Masa —"}
                            </span>
                            <span className={styles.orderNumber}>#{order.orderNumber ?? "—"}</span>
                          </div>
                          <span className={styles.orderTotal}>{formatPriceMinorUnits(order.totalMinorUnits)}</span>
                        </div>

                        <div className={styles.cardMeta}>
                          <Badge tone={WAITING_BADGE_TONE[urgency]}>{formatElapsedMinutes(order.statusSince, now)} bekliyor</Badge>
                        </div>

                        {order.items.map((item) => (
                          <div key={item.id} className={styles.item}>
                            {item.orderedQuantity}× {item.productName}
                            {item.options.length > 0 ? (
                              <div className={styles.itemOptions}>{item.options.map((option) => option.name).join(", ")}</div>
                            ) : null}
                          </div>
                        ))}

                        <div className={styles.cardActions}>
                          <Button disabled={readyingOrderId === order.orderId} onClick={() => handleMarkReady(order.orderId)}>
                            <BellRing size={16} aria-hidden="true" />
                            Hazır
                          </Button>
                        </div>
                      </article>
                    );
                  })}
                </div>
              )}
            </section>

            <section className={`${styles.column} ${styles["column--ready"]}`}>
              <header className={styles.columnHeader}>
                <span className={styles.columnIcon} aria-hidden="true">
                  <BellRing size={16} />
                </span>
                <h2 className={styles.columnTitle}>Hazır · Teslim Bekliyor</h2>
                <span className={styles.columnCount}>{readyOrders.length}</span>
              </header>
              {readyOrders.length === 0 ? (
                <EmptyState
                  icon={<BellRing size={28} aria-hidden="true" />}
                  title="Teslim bekleyen sipariş yok"
                  description="Hazırlanan siparişler burada görünecek."
                />
              ) : (
                <div className={styles.columnBody}>
                  {readyOrders.map((order) => (
                    <article key={order.orderId} className={`${styles.card} ${styles["card--ready"]}`}>
                      <div className={styles.cardHeader}>
                        <div className={styles.cardHeaderMain}>
                          <span className={styles.tableLabel}>
                            <UtensilsCrossed size={14} className={styles.tableLabelIcon} aria-hidden="true" />
                            {order.tableLabel ?? "Masa —"}
                          </span>
                          <span className={styles.orderNumber}>#{order.orderNumber ?? "—"}</span>
                        </div>
                        <span className={styles.orderTotal}>{formatPriceMinorUnits(order.totalMinorUnits)}</span>
                      </div>

                      <div className={styles.cardMeta}>
                        <Badge tone="success">Hazır</Badge>
                      </div>

                      {order.items.map((item) => (
                        <div key={item.id} className={styles.item}>
                          {item.orderedQuantity}× {item.productName}
                          {item.options.length > 0 ? (
                            <div className={styles.itemOptions}>{item.options.map((option) => option.name).join(", ")}</div>
                          ) : null}
                        </div>
                      ))}

                      <div className={styles.cardActions}>
                        <Button disabled={completingOrderId === order.orderId} onClick={() => handleComplete(order.orderId)}>
                          <Send size={16} aria-hidden="true" />
                          Teslim Edildi / Tamamlandı
                        </Button>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>
          </div>
        )}
      </main>
    </AppShell>
  );
}
