"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  ApiError,
  buildKitchenStreamUrl,
  decideOrderItem,
  formatPriceMinorUnits,
  getKitchenFinancialSummary,
  getKitchenQueue,
  markOrderItemReady,
  markOrderItemServed,
  me,
  type KitchenFinancialSummary,
  type KitchenOrder,
} from "@/lib/api";
import StaffNav from "@/components/layout/StaffNav";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import styles from "./page.module.css";

const ITEM_STATUS_LABELS: Record<string, string> = {
  PENDING_REVIEW: "Onay bekliyor",
  PREPARING: "Hazırlanıyor",
  REJECTED: "Reddedildi",
  READY: "Hazır",
  SERVED: "Teslim edildi",
};

/**
 * The KDS board (Section 4, staff-web screen #5). Loads the current IN_KITCHEN queue
 * once via REST, then relies on the SSE stream purely as a "something changed, refetch"
 * signal (Section 2: SSE chosen over WebSocket for its simplicity) rather than trying
 * to apply granular deltas client-side - simpler and can't drift out of sync with the
 * server's actual state. Milestone 8: auth is the qrmenu_staff_session cookie (sent
 * automatically by fetch's credentials:'include' and EventSource's withCredentials)
 * instead of the old localStorage shared token.
 */
export default function KitchenBoardPage() {
  const params = useParams<{ branchId: string }>();
  const branchId = params.branchId;
  const router = useRouter();

  const [orders, setOrders] = useState<KitchenOrder[]>([]);
  const [connectionStatus, setConnectionStatus] = useState<"connecting" | "live" | "reconnecting">("connecting");
  const [error, setError] = useState<string | null>(null);
  const [pendingItemId, setPendingItemId] = useState<string | null>(null);
  const [financialSummary, setFinancialSummary] = useState<KitchenFinancialSummary | null>(null);
  const acceptedInputRef = useRef<Record<string, number>>({});
  // Shared by reloadQueue (button handlers) and the SSE effect's own fetchQueue below
  // - two triggers (a manual mutation and an SSE "order-status" event firing almost
  // simultaneously) can each start a fetch, and nothing guarantees they resolve in
  // the order they were sent. Discarding a response whose requestId isn't the latest
  // one issued prevents a late, stale response from overwriting newer state.
  const latestRequestIdRef = useRef(0);

  const reloadQueue = useCallback(async () => {
    const requestId = ++latestRequestIdRef.current;
    try {
      const data = await getKitchenQueue(branchId);
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
  }, [branchId, router]);

  useEffect(() => {
    let cancelled = false;

    // Subscribes to the branch's live SSE stream (Section 2) and does the matching
    // initial fetch - both belong in one effect since they share the same
    // branchId lifecycle (open connection, load once, tear down together).
    // fetchQueue is deliberately a local closure (not the outer reloadQueue
    // useCallback) so every state update it makes happens inside a callback -
    // the SSE "order-status" listener or this IIFE - rather than the effect body
    // itself calling a setState-bearing function synchronously.
    async function fetchQueue() {
      const requestId = ++latestRequestIdRef.current;
      try {
        const data = await getKitchenQueue(branchId);
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
    eventSource.addEventListener("order-status", () => fetchQueue());
    void fetchQueue();

    return () => {
      cancelled = true;
      eventSource.close();
    };
  }, [branchId, router]);

  useEffect(() => {
    let cancelled = false;
    // Gap-analysis #14: REPORT_FINANCIAL_SUMMARY_VIEW is only granted to
    // BUSINESS_ADMIN/BRANCH_MANAGER (see StaffRole) - checking the role here just
    // avoids a call that would 403 for CASHIER/KITCHEN_STAFF; the backend enforces
    // this regardless of what this check does.
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

  async function handleDecide(orderItemId: string, orderedQuantity: number) {
    const acceptedQuantity = acceptedInputRef.current[orderItemId] ?? orderedQuantity;
    setPendingItemId(orderItemId);
    try {
      await decideOrderItem(branchId, orderItemId, acceptedQuantity);
      await reloadQueue();
    } catch {
      setError("Karar kaydedilemedi.");
    } finally {
      setPendingItemId(null);
    }
  }

  async function handleReady(orderItemId: string) {
    setPendingItemId(orderItemId);
    try {
      await markOrderItemReady(branchId, orderItemId);
      await reloadQueue();
    } catch {
      setError("Güncellenemedi.");
    } finally {
      setPendingItemId(null);
    }
  }

  async function handleServed(orderItemId: string) {
    setPendingItemId(orderItemId);
    try {
      await markOrderItemServed(branchId, orderItemId);
      await reloadQueue();
    } catch {
      setError("Güncellenemedi.");
    } finally {
      setPendingItemId(null);
    }
  }

  return (
    <>
      <StaffNav />
      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>Mutfak</h1>
          <div className={styles.headerActions}>
            <Link href={`/cashier/${branchId}`} className={styles.refundsLink}>
              Kasa
            </Link>
            <Link href={`/refunds/${branchId}`} className={styles.refundsLink}>
              İade İşlemleri
            </Link>
            <span className={styles.connectionStatus}>
              {connectionStatus === "live" ? "Canlı" : connectionStatus === "reconnecting" ? "Yeniden bağlanıyor…" : "Bağlanıyor…"}
            </span>
          </div>
        </div>

        {error ? <p className={styles.connectionStatus}>{error}</p> : null}

        {financialSummary ? (
          <div className={styles.financialSummary}>
            <div className={styles.financialSummaryItem}>
              <span className={styles.financialSummaryLabel}>Bugün brüt satış</span>
              <span className={styles.financialSummaryValue}>{formatPriceMinorUnits(financialSummary.grossSalesMinorUnits)}</span>
            </div>
            <div className={styles.financialSummaryItem}>
              <span className={styles.financialSummaryLabel}>Net satış</span>
              <span className={styles.financialSummaryValue}>{formatPriceMinorUnits(financialSummary.netSalesMinorUnits)}</span>
            </div>
            <div className={styles.financialSummaryItem}>
              <span className={styles.financialSummaryLabel}>Sipariş sayısı</span>
              <span className={styles.financialSummaryValue}>{financialSummary.orderCount}</span>
            </div>
          </div>
        ) : null}

        {orders.length === 0 ? (
          <p className={styles.connectionStatus}>Şu an mutfağa düşen sipariş yok.</p>
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
                    <p className={styles.itemName}>
                      {item.orderedQuantity}× {item.productName}
                    </p>
                    {item.options.length > 0 ? (
                      <p className={styles.itemOptions}>{item.options.map((option) => option.name).join(", ")}</p>
                    ) : null}
                    <p className={styles.itemMeta}>
                      <Badge tone={item.status === "REJECTED" ? "danger" : "neutral"}>{ITEM_STATUS_LABELS[item.status] ?? item.status}</Badge>
                    </p>

                    {item.status === "PENDING_REVIEW" ? (
                      <div className={styles.itemActions}>
                        <input
                          type="number"
                          className={styles.quantityInput}
                          min={0}
                          max={item.orderedQuantity}
                          defaultValue={item.orderedQuantity}
                          onChange={(event) => {
                            acceptedInputRef.current[item.id] = Number(event.target.value);
                          }}
                          aria-label="Kabul edilen adet"
                        />
                        <Button size="md" disabled={pendingItemId === item.id} onClick={() => handleDecide(item.id, item.orderedQuantity)}>
                          Onayla
                        </Button>
                      </div>
                    ) : null}

                    {item.status === "PREPARING" ? (
                      <div className={styles.itemActions}>
                        <Button size="md" disabled={pendingItemId === item.id} onClick={() => handleReady(item.id)}>
                          Hazır
                        </Button>
                      </div>
                    ) : null}

                    {item.status === "READY" ? (
                      <div className={styles.itemActions}>
                        <Button size="md" variant="secondary" disabled={pendingItemId === item.id} onClick={() => handleServed(item.id)}>
                          Teslim Edildi
                        </Button>
                      </div>
                    ) : null}
                  </div>
                ))}
              </article>
            ))}
          </div>
        )}
      </main>
    </>
  );
}
