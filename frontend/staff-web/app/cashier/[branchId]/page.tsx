"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { redirect, useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  BellRing,
  CalendarDays,
  Check,
  ChefHat,
  Clock,
  Loader2,
  Receipt,
  RefreshCw,
  Search,
  Send,
  Undo2,
  UtensilsCrossed,
  Wallet,
  Timer,
} from "lucide-react";
import {
  ApiError,
  acceptOrder,
  buildOrderStreamUrl,
  completeOrder,
  formatPriceMinorUnits,
  getInProgressOrders,
  getBranchSalesReport,
  getKitchenFinancialSummary,
  getPendingAcceptanceOrders,
  getReadyOrders,
  markOrderReady,
  me,
  rejectOrder,
  type KitchenFinancialSummary,
  type OrderControlOrder,
} from "@/lib/api";
import { branchIsoDate, formatElapsedMinutes, waitingUrgency } from "@/lib/time";
import { playCriticalOrderAlert } from "@/lib/alertSound";
import AppShell from "@/components/layout/AppShell";
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

function formatClockTime(iso: string): string {
  return new Date(iso).toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" });
}

function formatPreparationDuration(totalSeconds: number): string {
  if (totalSeconds < 60) {
    return `${totalSeconds} sn`;
  }
  return `${Math.round(totalSeconds / 60)} dk`;
}

function matchesSearch(order: OrderControlOrder, query: string): boolean {
  if (!query) {
    return true;
  }
  const haystack = `${order.tableLabel ?? ""} ${order.orderNumber ?? ""}`.toLowerCase();
  return haystack.includes(query);
}

/**
 * Ürün kararı: ayrı bir Mutfak/KDS ekranı yok - Kasa, sipariş operasyonunun tek ekranı.
 * Üç liste tek sayfada yaşar: onay bekleyen siparişler (kabul/red), kabul edilmiş ve
 * hâlâ hazırlanan siparişler (tek "Hazır" aksiyonuyla PREPARING -> READY, item bazlı bir
 * karar adımı yok) ve hazır/teslim bekleyen siparişler. Üç liste de aynı SSE kanalını
 * (Section 2: "salt refetch sinyali") paylaşır - branch'in order-control kanalı zaten
 * her durum geçişinde event yayınlıyor.
 *
 * Görsel yön: docs/design/QR-Code-Kasa Ekranı Tasarımı.png mockup'ına uyarlandı (bkz.
 * development-progress.md "Kasa Ekranı - Görsel Referansa Uyarlama"). Mockup'ın "kişi
 * sayısı", "ortalama hazırlık süresi", "dünkü güne göre %" ve bildirim rozeti gibi
 * karşılığı backend'de olmayan alanları eklenmedi - yalnızca gerçek veriyle
 * doldurulabilen alanlar taşındı.
 */
export default function LegacyCashierPage() {
  const params = useParams<{ branchId?: string }>();
  if (params.branchId) redirect("/cashier");
  return <CashierDashboardPage />;
}

function CashierDashboardPage() {
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
  const [operationalMetrics, setOperationalMetrics] = useState<{
    averagePreparationSeconds: number;
    completedOrderCount: number;
  } | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [now, setNow] = useState(() => Date.now());
  const reasonCodeRef = useRef<Record<string, string>>({});
  const noteRef = useRef<Record<string, string>>({});
  const latestRequestIdRef = useRef(0);
  // Section 6: kritik alarmı sipariş başına yalnızca bir kez çalar - her 15sn'lik "now"
  // tazelemesinde tekrar tekrar öttürmemek için hangi siparişler için zaten uyarıldığını tutar.
  const alertedOrderIdsRef = useRef<Set<string>>(new Set());

  const todayLabel = useMemo(() => {
    const today = new Date();
    const date = new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "long", year: "numeric" }).format(today);
    const weekday = new Intl.DateTimeFormat("tr-TR", { weekday: "long" }).format(today);
    return `${date}, ${weekday}`;
  }, []);

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

  // Gap-analysis #14: REPORT_FINANCIAL_SUMMARY_VIEW is only granted to
  // BUSINESS_ADMIN/BRANCH_MANAGER (see StaffRole) - checking the role here just
  // avoids a call that would 403 for CASHIER; the backend enforces this regardless.
  const refreshMetrics = useCallback(async () => {
    try {
      const context = await me();
      // Branch-local "today", not the device's - agrees with the backend's own
      // TenantService.resolveBranchTimeZone-based day boundary (see lib/time.ts).
      const today = branchIsoDate(context.activeBranchTimeZone);
      const operationalRequest = getBranchSalesReport(today, today).then((report) => {
        setOperationalMetrics({
          averagePreparationSeconds: report.averagePreparationSeconds,
          completedOrderCount: report.completedOrderCount,
        });
      });
      if (context.role !== "BUSINESS_ADMIN" && context.role !== "BRANCH_MANAGER") {
        await operationalRequest;
        return;
      }
      await Promise.all([
        operationalRequest,
        getKitchenFinancialSummary(today, today).then((summary) => {
          setFinancialSummary(summary);
        }),
      ]);
    } catch {
      // Best-effort KPI refresh - the order-control lists above are the source of truth
      // for the board and already surface their own errors.
    }
  }, []);

  const reloadAll = useCallback(async () => {
    const requestId = ++latestRequestIdRef.current;
    try {
      const [pending, inProgress, ready] = await Promise.all([
        getPendingAcceptanceOrders(),
        getInProgressOrders(),
        getReadyOrders(),
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
    void refreshMetrics();
  }, [router, refreshMetrics]);

  useEffect(() => {
    let cancelled = false;

    async function fetchAll() {
      const requestId = ++latestRequestIdRef.current;
      try {
        const [pending, inProgress, ready] = await Promise.all([
          getPendingAcceptanceOrders(),
          getInProgressOrders(),
          getReadyOrders(),
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

    const eventSource = new EventSource(buildOrderStreamUrl(), { withCredentials: true });
    // The stream itself never replays a backlog (Section 2: "initial connect delivers no
    // backlog") - a status change that lands while the connection is down/reconnecting
    // (network blip, laptop sleep) would otherwise never reach this board until some later,
    // unrelated event happens to trigger a refetch. Refetching on "open" (fired both for the
    // first connect and every reconnect) closes that gap - same fix as the customer tracking
    // page's unconditional reload on visibilitychange/reconnect.
    eventSource.addEventListener("open", () => {
      setConnectionStatus("live");
      void fetchAll();
    });
    eventSource.addEventListener("error", () => setConnectionStatus("reconnecting"));
    eventSource.addEventListener("order-status", () => {
      void fetchAll();
      void refreshMetrics();
    });
    void fetchAll();

    return () => {
      cancelled = true;
      eventSource.close();
    };
  }, [router, refreshMetrics]);

  useEffect(() => {
    void refreshMetrics();
  }, [refreshMetrics]);

  async function handleAccept(orderId: string) {
    setPendingOrderId(orderId);
    setError(null);
    try {
      await acceptOrder(orderId);
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
      await rejectOrder(orderId, reasonCode, note);
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
      await markOrderReady(orderId);
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
      await completeOrder(orderId);
      await reloadAll();
    } catch {
      setError("Sipariş tamamlanamadı.");
    } finally {
      setCompletingOrderId(null);
    }
  }

  const normalizedQuery = searchQuery.trim().toLowerCase();
  const filteredPending = pendingOrders.filter((order) => matchesSearch(order, normalizedQuery));
  const filteredInProgress = inProgressOrders.filter((order) => matchesSearch(order, normalizedQuery));
  const filteredReady = readyOrders.filter((order) => matchesSearch(order, normalizedQuery));
  const searchActive = normalizedQuery.length > 0;

  return (
    <AppShell>
      <main className={styles.page}>
        <div className={styles.toolbar}>
          <span className={styles.dateChip}>
            <CalendarDays size={15} aria-hidden="true" />
            {todayLabel}
          </span>
          <label className={styles.searchBar}>
            <Search size={16} aria-hidden="true" />
            <input
              type="text"
              className={styles.searchInput}
              placeholder="Sipariş veya masa ara..."
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              aria-label="Sipariş veya masa ara"
            />
          </label>
          <button type="button" className={styles.refreshButton} onClick={() => reloadAll()} disabled={loading}>
            <RefreshCw size={15} aria-hidden="true" />
            Yenile
          </button>
        </div>

        <div className={styles.header}>
          <h1 className={styles.title}>Kasa</h1>
          <div className={styles.headerActions}>
            <Link href="/refunds" className={styles.navLink}>
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

        <div className={styles.kpiGrid}>
          <div className={styles.kpiCard}>
            <span className={styles.kpiIcon} aria-hidden="true">
              <Wallet size={22} />
            </span>
            <span className={styles.kpiBody}>
              <span className={styles.kpiLabel}>Günlük Ciro</span>
              <span className={styles.kpiValue}>
                {financialSummary ? formatPriceMinorUnits(financialSummary.grossSalesMinorUnits) : "—"}
              </span>
            </span>
          </div>
          <div className={styles.kpiCard}>
            <span className={styles.kpiIcon} aria-hidden="true">
              <Receipt size={22} />
            </span>
            <span className={styles.kpiBody}>
              <span className={styles.kpiLabel}>Toplam Sipariş</span>
              <span className={styles.kpiValue}>{financialSummary?.orderCount ?? "—"}</span>
            </span>
          </div>
          <div className={styles.kpiCard}>
            <span className={styles.kpiIcon} aria-hidden="true">
              <Clock size={22} />
            </span>
            <span className={styles.kpiBody}>
              <span className={styles.kpiLabel}>Ortalama Hazırlık Süresi</span>
              <span className={styles.kpiValue}>
                {operationalMetrics ? formatPreparationDuration(operationalMetrics.averagePreparationSeconds) : "—"}
              </span>
            </span>
          </div>
          <div className={styles.kpiCard}>
            <span className={styles.kpiIcon} aria-hidden="true">
              <Check size={22} />
            </span>
            <span className={styles.kpiBody}>
              <span className={styles.kpiLabel}>Tamamlanan Sipariş</span>
              <span className={styles.kpiValue}>{operationalMetrics?.completedOrderCount ?? "—"}</span>
            </span>
          </div>
        </div>

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
                <span className={styles.columnCount}>{filteredPending.length}</span>
              </header>
              {filteredPending.length === 0 ? (
                <EmptyState
                  icon={<Timer size={28} aria-hidden="true" />}
                  title={searchActive ? "Eşleşen sipariş yok" : "Onay bekleyen sipariş yok"}
                  description={searchActive ? "Arama kriterine uyan bir sipariş bulunamadı." : "Ödemesi tamamlanan yeni siparişler burada görünecek."}
                />
              ) : (
                <div className={styles.columnBody}>
                  {filteredPending.map((order) => {
                    const urgency = waitingUrgency(order.statusSince, now, order.storeAcceptanceTimeoutSeconds);
                    return (
                      <article
                        key={order.orderId}
                        className={[styles.card, styles["card--pending"], urgency === "danger" ? styles["card--critical"] : ""].join(" ")}
                      >
                        <div className={styles.cardTop}>
                          <span className={styles.tableChip} aria-hidden="true">
                            <UtensilsCrossed size={16} />
                          </span>
                          <div className={styles.cardTopMain}>
                            <span className={styles.tableName}>{order.tableLabel ?? "Masa —"}</span>
                            <span className={styles.orderNumber}>#{order.orderNumber ?? "—"}</span>
                          </div>
                          <div className={styles.cardTopTime}>
                            <span className={styles.timeValue}>
                              <Clock size={13} aria-hidden="true" />
                              {formatElapsedMinutes(order.statusSince, now)}
                            </span>
                            <span className={styles.timeSub}>Bugün {formatClockTime(order.statusSince)}</span>
                          </div>
                        </div>

                        <div className={styles.items}>
                          {order.items.map((item) => (
                            <div key={item.id} className={styles.itemRow}>
                              <span className={styles.itemBullet} aria-hidden="true" />
                              <span className={styles.itemName}>
                                {item.orderedQuantity}x {item.productName}
                                {item.options.length > 0 ? (
                                  <span className={styles.itemOptions}> · {item.options.map((option) => option.name).join(", ")}</span>
                                ) : null}
                              </span>
                            </div>
                          ))}
                        </div>

                        <div className={styles.cardFooter}>
                          <div className={styles.totalRow}>
                            <span className={styles.totalLabel}>Toplam</span>
                            <span className={styles.totalValue}>{formatPriceMinorUnits(order.totalMinorUnits)}</span>
                          </div>

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
                              <Button
                                variant="secondary"
                                disabled={pendingOrderId === order.orderId}
                                onClick={() => setRejectingOrderId(order.orderId)}
                              >
                                Reddet
                              </Button>
                              <Button
                                className={styles.acceptButton}
                                disabled={pendingOrderId === order.orderId}
                                onClick={() => handleAccept(order.orderId)}
                              >
                                <Check size={16} aria-hidden="true" />
                                Siparişi Onayla
                              </Button>
                            </div>
                          )}
                        </div>
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
                <span className={styles.columnCount}>{filteredInProgress.length}</span>
              </header>
              {filteredInProgress.length === 0 ? (
                <EmptyState
                  icon={<ChefHat size={28} aria-hidden="true" />}
                  title={searchActive ? "Eşleşen sipariş yok" : "Hazırlanan sipariş yok"}
                  description={searchActive ? "Arama kriterine uyan bir sipariş bulunamadı." : "Kabul edilen siparişler burada görünecek."}
                />
              ) : (
                <div className={styles.columnBody}>
                  {filteredInProgress.map((order) => (
                    <article key={order.orderId} className={[styles.card, styles["card--preparing"]].join(" ")}>
                      <div className={styles.cardTop}>
                        <span className={styles.tableChip} aria-hidden="true">
                          <UtensilsCrossed size={16} />
                        </span>
                        <div className={styles.cardTopMain}>
                          <span className={styles.tableName}>{order.tableLabel ?? "Masa —"}</span>
                          <span className={styles.orderNumber}>#{order.orderNumber ?? "—"}</span>
                        </div>
                        <div className={styles.cardTopTime}>
                          <span className={styles.timeValue}>
                            <Clock size={13} aria-hidden="true" />
                            {formatElapsedMinutes(order.statusSince, now)}
                          </span>
                          <span className={styles.timeSub}>{formatClockTime(order.statusSince)}&apos;te alındı</span>
                        </div>
                      </div>

                      <div className={styles.items}>
                        {order.items.map((item) => (
                          <div key={item.id} className={styles.itemRow}>
                            <span className={styles.itemBullet} aria-hidden="true" />
                            <span className={styles.itemName}>
                              {item.orderedQuantity}x {item.productName}
                              {item.options.length > 0 ? (
                                <span className={styles.itemOptions}> · {item.options.map((option) => option.name).join(", ")}</span>
                              ) : null}
                            </span>
                          </div>
                        ))}
                      </div>

                      <div className={styles.cardFooter}>
                        <div className={styles.totalRow}>
                          <span className={styles.totalLabel}>Toplam</span>
                          <span className={styles.totalValue}>{formatPriceMinorUnits(order.totalMinorUnits)}</span>
                        </div>
                        <div className={styles.cardActions}>
                          <Button
                            className={styles.acceptButton}
                            disabled={readyingOrderId === order.orderId}
                            onClick={() => handleMarkReady(order.orderId)}
                          >
                            <ChefHat size={16} aria-hidden="true" />
                            Hazırlığı Tamamla
                          </Button>
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>

            <section className={`${styles.column} ${styles["column--ready"]}`}>
              <header className={styles.columnHeader}>
                <span className={styles.columnIcon} aria-hidden="true">
                  <BellRing size={16} />
                </span>
                <h2 className={styles.columnTitle}>Hazır · Teslim Bekliyor</h2>
                <span className={styles.columnCount}>{filteredReady.length}</span>
              </header>
              {filteredReady.length === 0 ? (
                <EmptyState
                  icon={<BellRing size={28} aria-hidden="true" />}
                  title={searchActive ? "Eşleşen sipariş yok" : "Teslim bekleyen sipariş yok"}
                  description={searchActive ? "Arama kriterine uyan bir sipariş bulunamadı." : "Hazırlanan siparişler burada görünecek."}
                />
              ) : (
                <div className={styles.columnBody}>
                  {filteredReady.map((order) => (
                    <article key={order.orderId} className={[styles.card, styles["card--ready"]].join(" ")}>
                      <div className={styles.cardTop}>
                        <span className={styles.tableChip} aria-hidden="true">
                          <UtensilsCrossed size={16} />
                        </span>
                        <div className={styles.cardTopMain}>
                          <span className={styles.tableName}>{order.tableLabel ?? "Masa —"}</span>
                          <span className={styles.orderNumber}>#{order.orderNumber ?? "—"}</span>
                        </div>
                        <div className={styles.cardTopTime}>
                          <span className={styles.timeValue}>
                            <Clock size={13} aria-hidden="true" />
                            {formatElapsedMinutes(order.statusSince, now)}
                          </span>
                          <span className={styles.timeSub}>{formatClockTime(order.statusSince)}&apos;de hazır</span>
                        </div>
                      </div>

                      <div className={styles.items}>
                        {order.items.map((item) => (
                          <div key={item.id} className={styles.itemRow}>
                            <span className={styles.itemBullet} aria-hidden="true" />
                            <span className={styles.itemName}>
                              {item.orderedQuantity}x {item.productName}
                              {item.options.length > 0 ? (
                                <span className={styles.itemOptions}> · {item.options.map((option) => option.name).join(", ")}</span>
                              ) : null}
                            </span>
                          </div>
                        ))}
                      </div>

                      <div className={styles.cardFooter}>
                        <div className={styles.totalRow}>
                          <span className={styles.totalLabel}>Toplam</span>
                          <span className={styles.totalValue}>{formatPriceMinorUnits(order.totalMinorUnits)}</span>
                        </div>
                        <div className={styles.cardActions}>
                          <Button
                            className={styles.deliverButton}
                            disabled={completingOrderId === order.orderId}
                            onClick={() => handleComplete(order.orderId)}
                          >
                            <Send size={16} aria-hidden="true" />
                            Teslim Et
                          </Button>
                        </div>
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
