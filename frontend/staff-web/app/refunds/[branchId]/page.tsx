"use client";

import { useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ApiError, completeOrder, createRefund, formatPriceMinorUnits, searchOrderByNumber, type StaffOrderLookup } from "@/lib/api";
import StaffNav from "@/components/layout/StaffNav";
import Button from "@/components/ui/Button";
import styles from "./page.module.css";

/**
 * Section 4, staff-web screen #6: "tam/kısmi iade başlatma, teslim işlemi" - staff finds
 * an order by its readable order number, then either picks a refund quantity per item
 * (backend prices each line from the OrderItem's own snapshot, never trusting a
 * client-supplied amount - Section 9) or, once the order is READY, marks it delivered/
 * picked up (Milestone 9, Permission.ORDER_COMPLETE). Permission.REFUND_ISSUE + branch
 * scoping via the qrmenu_staff_session cookie (Milestone 8).
 */
export default function RefundsPage() {
  const params = useParams<{ branchId: string }>();
  const branchId = params.branchId;
  const router = useRouter();

  const [orderNumberInput, setOrderNumberInput] = useState("");
  const [order, setOrder] = useState<StaffOrderLookup | null>(null);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const quantityInputsRef = useRef<Record<string, number>>({});

  async function handleSearch(event: React.FormEvent) {
    event.preventDefault();
    const orderNumber = Number(orderNumberInput);
    if (!Number.isInteger(orderNumber) || orderNumber <= 0) {
      setError("Geçerli bir sipariş numarası girin.");
      return;
    }
    setSearching(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const found = await searchOrderByNumber(branchId, orderNumber);
      setOrder(found);
      // The quantity inputs below render with a defaultValue (rejectedQuantity, or 0)
      // rather than a controlled value - React never reports that initial value
      // through onChange, so the ref driving submission must be seeded with the same
      // defaults here, or a refund submitted without ever touching the inputs (the
      // common case: the suggested default is exactly what staff wants) would send no
      // lines at all.
      quantityInputsRef.current = Object.fromEntries(
        found.items.map((item) => [item.id, item.rejectedQuantity > 0 ? item.rejectedQuantity : 0]),
      );
    } catch (err) {
      setOrder(null);
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        router.replace("/");
        return;
      }
      setError(err instanceof ApiError && err.status === 404 ? "Bu numarada bir sipariş bulunamadı." : "Sipariş aranırken bir sorun oluştu.");
    } finally {
      setSearching(false);
    }
  }

  async function handleSubmitRefund() {
    if (!order) {
      return;
    }
    const items = Object.entries(quantityInputsRef.current)
      .filter(([, quantity]) => quantity > 0)
      .map(([orderItemId, quantity]) => ({ orderItemId, quantity }));
    if (items.length === 0) {
      setError("Lütfen en az bir kalem için iade adedi girin.");
      return;
    }
    setSubmitting(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const refund = await createRefund(branchId, order.orderId, items);
      setSuccessMessage(`İade tamamlandı: ${formatPriceMinorUnits(refund.totalAmountMinorUnits)}`);
      const refreshed = await searchOrderByNumber(branchId, order.orderNumber ?? 0);
      setOrder(refreshed);
      // Reset every input to 0 (not the rejectedQuantity default) - that quantity may
      // already have just been refunded, and re-suggesting it would invite an
      // accidental duplicate refund attempt.
      quantityInputsRef.current = Object.fromEntries(refreshed.items.map((item) => [item.id, 0]));
    } catch {
      setError("İade işlenemedi (tutar, kalan iade edilebilir tutarı aşıyor olabilir).");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleComplete() {
    if (!order) {
      return;
    }
    setCompleting(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const updated = await completeOrder(branchId, order.orderId);
      setOrder(updated);
      setSuccessMessage("Sipariş tamamlandı olarak işaretlendi.");
    } catch {
      setError("Sipariş tamamlanamadı.");
    } finally {
      setCompleting(false);
    }
  }

  return (
    <>
      <StaffNav />
      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>İade İşlemleri</h1>
          <Link href={`/kitchen/${branchId}`} className={styles.backLink}>
            Mutfağa dön
          </Link>
        </div>

        <form className={styles.searchRow} onSubmit={handleSearch}>
          <input
            className={styles.input}
            placeholder="Sipariş No"
            inputMode="numeric"
            value={orderNumberInput}
            onChange={(event) => setOrderNumberInput(event.target.value)}
          />
          <Button type="submit" disabled={searching}>
            {searching ? "Aranıyor…" : "Ara"}
          </Button>
        </form>

        {error ? <p className={styles.error}>{error}</p> : null}
        {successMessage ? <p className={styles.success}>{successMessage}</p> : null}

        {order ? (
          <div className={styles.orderCard}>
            <div className={styles.orderHeader}>
              <span className={styles.orderNumber}>#{order.orderNumber}</span>
              <span>{formatPriceMinorUnits(order.totalMinorUnits)}</span>
              <span>{order.status}</span>
            </div>

            {order.status === "READY" ? (
              <div className={styles.submitRow}>
                <Button size="lg" variant="secondary" disabled={completing} onClick={handleComplete}>
                  {completing ? "İşleniyor…" : "Teslim Edildi / Alındı"}
                </Button>
              </div>
            ) : null}

            {order.items.map((item) => (
              <div key={item.id} className={styles.item}>
                <div className={styles.itemInfo}>
                  <p className={styles.itemName}>
                    {item.orderedQuantity}× {item.productName}
                  </p>
                  <p className={styles.itemMeta}>
                    {formatPriceMinorUnits(item.unitPriceMinorUnits)} / adet
                    {item.rejectedQuantity > 0 ? ` · ${item.rejectedQuantity} adet reddedildi` : ""}
                  </p>
                </div>
                <input
                  type="number"
                  className={styles.quantityInput}
                  min={0}
                  max={item.orderedQuantity}
                  defaultValue={item.rejectedQuantity > 0 ? item.rejectedQuantity : 0}
                  onChange={(event) => {
                    quantityInputsRef.current[item.id] = Number(event.target.value);
                  }}
                  aria-label={`${item.productName} için iade adedi`}
                />
              </div>
            ))}

            <div className={styles.submitRow}>
              <Button size="lg" disabled={submitting} onClick={handleSubmitRefund}>
                {submitting ? "İşleniyor…" : "İade Başlat"}
              </Button>
            </div>

            {order.refunds.length > 0 ? (
              <div className={styles.refundsSection}>
                <p className={styles.refundsTitle}>Geçmiş İadeler</p>
                {order.refunds.map((refund) => (
                  <div key={refund.refundId} className={styles.refundEntry}>
                    <span>{new Date(refund.createdAt).toLocaleString("tr-TR")}</span>
                    <span>{formatPriceMinorUnits(refund.totalAmountMinorUnits)}</span>
                  </div>
                ))}
              </div>
            ) : null}
          </div>
        ) : null}
      </main>
    </>
  );
}
