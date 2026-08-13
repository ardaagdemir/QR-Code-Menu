"use client";

import { useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ApiError, completeOrder, createRefund, formatPriceMinorUnits, searchOrderByNumber, type StaffOrderLookup } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import ErrorState from "@/components/ui/ErrorState";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import { useToast } from "@/components/ui/ToastProvider";
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
  const { showToast } = useToast();

  const [orderNumberInput, setOrderNumberInput] = useState("");
  const [order, setOrder] = useState<StaffOrderLookup | null>(null);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [confirmingRefundQuantity, setConfirmingRefundQuantity] = useState<number | null>(null);
  const quantityInputsRef = useRef<Record<string, number>>({});

  async function handleSearch(event: React.FormEvent) {
    event.preventDefault();
    const orderNumber = Number(orderNumberInput);
    if (!Number.isInteger(orderNumber) || orderNumber <= 0) {
      setSearchError("Geçerli bir sipariş numarası girin.");
      return;
    }
    setSearching(true);
    setSearchError(null);
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
      setSearchError(err instanceof ApiError && err.status === 404 ? "Bu numarada bir sipariş bulunamadı." : "Sipariş aranırken bir sorun oluştu.");
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
      showToast("Lütfen en az bir kalem için iade adedi girin.", "error");
      return;
    }
    setSubmitting(true);
    try {
      const refund = await createRefund(branchId, order.orderId, items);
      showToast(`İade tamamlandı: ${formatPriceMinorUnits(refund.totalAmountMinorUnits)}`, "success");
      const refreshed = await searchOrderByNumber(branchId, order.orderNumber ?? 0);
      setOrder(refreshed);
      // Reset every input to 0 (not the rejectedQuantity default) - that quantity may
      // already have just been refunded, and re-suggesting it would invite an
      // accidental duplicate refund attempt.
      quantityInputsRef.current = Object.fromEntries(refreshed.items.map((item) => [item.id, 0]));
    } catch {
      showToast("İade işlenemedi (tutar, kalan iade edilebilir tutarı aşıyor olabilir).", "error");
    } finally {
      setSubmitting(false);
      setConfirmingRefundQuantity(null);
    }
  }

  function handleRequestRefund() {
    const items = Object.entries(quantityInputsRef.current).filter(([, quantity]) => quantity > 0);
    if (items.length === 0) {
      showToast("Lütfen en az bir kalem için iade adedi girin.", "error");
      return;
    }
    setConfirmingRefundQuantity(items.reduce((sum, [, quantity]) => sum + quantity, 0));
  }

  async function handleComplete() {
    if (!order) {
      return;
    }
    setCompleting(true);
    try {
      const updated = await completeOrder(branchId, order.orderId);
      setOrder(updated);
      showToast("Sipariş tamamlandı olarak işaretlendi.", "success");
    } catch {
      showToast("Sipariş tamamlanamadı.", "error");
    } finally {
      setCompleting(false);
    }
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader
          title="İade İşlemleri"
          actions={
            <div className={styles.headerActions}>
              <Link href={`/cashier/${branchId}`} className={styles.backLink}>
                Kasa
              </Link>
            </div>
          }
        />

        <form className={styles.searchRow} onSubmit={handleSearch}>
          <Input
            placeholder="Sipariş No"
            aria-label="Sipariş numarası"
            inputMode="numeric"
            value={orderNumberInput}
            onChange={(event) => setOrderNumberInput(event.target.value)}
          />
          <Button type="submit" disabled={searching}>
            {searching ? "Aranıyor…" : "Ara"}
          </Button>
        </form>

        {searchError ? <ErrorState message={searchError} /> : null}

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
              <Button size="lg" disabled={submitting} onClick={handleRequestRefund}>
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

      {confirmingRefundQuantity !== null ? (
        <ConfirmDialog
          title="İadeyi Başlat"
          message={`#${order?.orderNumber} numaralı sipariş için ${confirmingRefundQuantity} kalem iade edilecek. Bu işlem geri alınamaz.`}
          confirmLabel="İade Başlat"
          tone="danger"
          confirmLoading={submitting}
          onConfirm={handleSubmitRefund}
          onCancel={() => setConfirmingRefundQuantity(null)}
        />
      ) : null}
    </AppShell>
  );
}
