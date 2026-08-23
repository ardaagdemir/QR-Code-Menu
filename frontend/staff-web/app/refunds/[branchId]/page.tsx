"use client";

import { useState } from "react";
import { redirect, useParams, useRouter } from "next/navigation";
import { AlertTriangle, PackageOpen, ReceiptText, RotateCcw, Search } from "lucide-react";
import { ApiError, completeOrder, createRefund, formatPriceMinorUnits, searchOrderByNumber, type StaffOrderLookup } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import Badge from "@/components/ui/Badge";
import Table from "@/components/ui/Table";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import { useToast } from "@/components/ui/ToastProvider";
import styles from "./page.module.css";

const ORDER_STATUS_LABELS: Record<string, string> = {
  DRAFT: "Taslak",
  CANCELLED: "İptal edildi",
  AWAITING_PAYMENT: "Ödeme bekliyor",
  PAYMENT_FAILED: "Ödeme başarısız",
  AWAITING_STORE_ACCEPTANCE: "Onay bekliyor",
  REJECTED_BY_STORE: "Reddedildi",
  IN_KITCHEN: "Hazırlanıyor",
  READY: "Teslime hazır",
  COMPLETED: "Tamamlandı",
};

const refundDateFormatter = new Intl.DateTimeFormat("tr-TR", {
  day: "numeric",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

const REFUND_STATUS_LABELS = {
  REQUESTED: "Talep edildi",
  PROCESSING: "İşleniyor",
  COMPLETED: "Tamamlandı",
  FAILED: "Başarısız",
} as const;

function refundStatusTone(status: keyof typeof REFUND_STATUS_LABELS): "neutral" | "success" | "danger" | "warning" {
  if (status === "COMPLETED") return "success";
  if (status === "FAILED") return "danger";
  if (status === "PROCESSING") return "warning";
  return "neutral";
}

function orderStatusTone(status: string): "neutral" | "success" | "danger" | "warning" | "info" {
  if (status === "READY" || status === "COMPLETED") return "success";
  if (status === "CANCELLED" || status === "PAYMENT_FAILED" || status === "REJECTED_BY_STORE") return "danger";
  if (status === "AWAITING_PAYMENT" || status === "AWAITING_STORE_ACCEPTANCE") return "warning";
  if (status === "IN_KITCHEN") return "info";
  return "neutral";
}

/**
 * Section 4, staff-web screen #6: "tam/kısmi iade başlatma, teslim işlemi" - staff finds
 * an order by its readable order number, then either picks a refund quantity per item
 * (backend prices each line from the OrderItem's own snapshot, never trusting a
 * client-supplied amount - Section 9) or, once the order is READY, marks it delivered/
 * picked up (Milestone 9, Permission.ORDER_COMPLETE). Permission.REFUND_ISSUE + branch
 * scoping via the qrmenu_staff_session cookie (Milestone 8).
 */
export default function LegacyRefundsPage() {
  const params = useParams<{ branchId?: string }>();
  if (params.branchId) redirect("/refunds");
  return <RefundsPage />;
}

function RefundsPage() {
  const router = useRouter();
  const { showToast } = useToast();

  const [orderNumberInput, setOrderNumberInput] = useState("");
  const [order, setOrder] = useState<StaffOrderLookup | null>(null);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [confirmingRefundQuantity, setConfirmingRefundQuantity] = useState<number | null>(null);
  const [quantityInputs, setQuantityInputs] = useState<Record<string, number>>({});

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
      const found = await searchOrderByNumber(orderNumber);
      setOrder(found);
      setQuantityInputs(Object.fromEntries(
        found.items.map((item) => [
          item.id,
          Math.min(item.rejectedQuantity, item.remainingRefundableQuantity),
        ]),
      ));
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
    const items = order.items
      .map((item) => ({ orderItemId: item.id, quantity: quantityInputs[item.id] ?? 0 }))
      .filter(({ quantity }) => quantity > 0);
    if (items.length === 0) {
      showToast("Lütfen en az bir kalem için iade adedi girin.", "error");
      return;
    }
    if (items.some(({ orderItemId, quantity }) => {
      const item = order.items.find((candidate) => candidate.id === orderItemId);
      return !item || quantity > item.remainingRefundableQuantity;
    })) {
      showToast("İade adedi kalan iade edilebilir adedi aşamaz.", "error");
      return;
    }
    setSubmitting(true);
    try {
      const refund = await createRefund(order.orderId, items);
      if (refund.status === "COMPLETED") {
        showToast(`İade tamamlandı: ${formatPriceMinorUnits(refund.totalAmountMinorUnits)}`, "success");
      } else if (refund.status === "FAILED") {
        showToast("İade sağlayıcı tarafından tamamlanamadı. Tekrar deneyebilirsiniz.", "error");
      } else {
        showToast("İade işleme alındı.", "info");
      }
      const refreshed = await searchOrderByNumber(order.orderNumber ?? 0);
      setOrder(refreshed);
      setQuantityInputs(Object.fromEntries(refreshed.items.map((item) => [item.id, 0])));
    } catch {
      if (order.orderNumber) {
        try {
          const refreshed = await searchOrderByNumber(order.orderNumber);
          setOrder(refreshed);
          setQuantityInputs(Object.fromEntries(refreshed.items.map((item) => [item.id, 0])));
        } catch {
          // Keep the current order visible if the best-effort refresh also fails.
        }
      }
      showToast("İade işlenemedi (tutar, kalan iade edilebilir tutarı aşıyor olabilir).", "error");
    } finally {
      setSubmitting(false);
      setConfirmingRefundQuantity(null);
    }
  }

  function handleRequestRefund() {
    if (!order) {
      return;
    }
    const items = order.items
      .map((item) => ({ item, quantity: quantityInputs[item.id] ?? 0 }))
      .filter(({ quantity }) => quantity > 0);
    if (items.length === 0) {
      showToast("Lütfen en az bir kalem için iade adedi girin.", "error");
      return;
    }
    if (items.some(({ item, quantity }) => quantity > item.remainingRefundableQuantity)) {
      showToast("İade adedi kalan iade edilebilir adedi aşamaz.", "error");
      return;
    }
    setConfirmingRefundQuantity(items.reduce((sum, { quantity }) => sum + quantity, 0));
  }

  async function handleComplete() {
    if (!order) {
      return;
    }
    setCompleting(true);
    try {
      const updated = await completeOrder(order.orderId);
      setOrder(updated);
      showToast("Sipariş tamamlandı olarak işaretlendi.", "success");
    } catch {
      showToast("Sipariş tamamlanamadı.", "error");
    } finally {
      setCompleting(false);
    }
  }

  const totalOrderedQuantity = order?.items.reduce((sum, item) => sum + item.orderedQuantity, 0) ?? 0;
  const totalRefundedQuantity = order?.items.reduce((sum, item) => sum + item.refundedQuantity, 0) ?? 0;
  const remainingRefundableQuantity = order?.items.reduce(
    (sum, item) => sum + item.remainingRefundableQuantity,
    0,
  ) ?? 0;
  const fullyRefunded = Boolean(order) && totalOrderedQuantity > 0 && remainingRefundableQuantity === 0;
  const partiallyRefunded = totalRefundedQuantity > 0 && !fullyRefunded;

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader
          title="İade İşlemleri"
          description="Sipariş numarasıyla ödeme ve ürün iadelerini yönetin."
        />

        <section className={styles.searchCard} aria-labelledby="refund-search-title">
          <div className={styles.searchHeading}>
            <span className={styles.sectionIcon} aria-hidden="true"><Search size={18} /></span>
            <div>
              <h2 id="refund-search-title" className={styles.searchTitle}>Sipariş Bul</h2>
              <p className={styles.searchDescription}>İade yapılacak siparişin numarasını girin.</p>
            </div>
          </div>
          <form className={styles.searchRow} onSubmit={handleSearch}>
            <div className={styles.searchField}>
              <label className={styles.searchLabel} htmlFor="refund-order-number">Sipariş numarası</label>
              <div className={styles.searchInputWrap}>
                <Search size={17} aria-hidden="true" />
                <Input
                  id="refund-order-number"
                  className={styles.searchInput}
                  placeholder="Örn. 1042"
                  inputMode="numeric"
                  value={orderNumberInput}
                  onChange={(event) => setOrderNumberInput(event.target.value)}
                />
              </div>
            </div>
            <Button className={styles.searchButton} type="submit" disabled={searching}>
              {searching ? "Aranıyor…" : "Siparişi Ara"}
            </Button>
          </form>
          {searchError ? <p className={styles.searchError} role="alert">{searchError}</p> : null}
        </section>

        {!order && !searchError ? (
          <div className={styles.emptyState} role="status">
            <span className={styles.emptyIcon} aria-hidden="true"><ReceiptText size={19} /></span>
            <p>İade işlemi için sipariş numarası girin.</p>
          </div>
        ) : null}

        {order ? (
          <section className={styles.orderCard}>
            <div className={styles.orderSummary}>
              <div className={styles.orderIdentity}>
                <span className={styles.orderIcon} aria-hidden="true"><ReceiptText size={20} /></span>
                <div>
                  <p className={styles.eyebrow}>Sipariş Bilgileri</p>
                  <div className={styles.orderTitleRow}>
                    <h2 className={styles.orderNumber}>Sipariş #{order.orderNumber}</h2>
                    <Badge tone={orderStatusTone(order.status)}>{ORDER_STATUS_LABELS[order.status] ?? order.status}</Badge>
                    {fullyRefunded ? (
                      <Badge tone="danger">Tam iade edildi</Badge>
                    ) : partiallyRefunded ? (
                      <Badge tone="warning">Kısmi iade</Badge>
                    ) : (
                      <Badge tone="neutral">İade yok</Badge>
                    )}
                  </div>
                </div>
              </div>
              <div className={styles.orderTotal}>
                <span>Sipariş toplamı</span>
                <strong>{formatPriceMinorUnits(order.totalMinorUnits)}</strong>
              </div>
              {order.status === "READY" ? (
                <Button variant="secondary" disabled={completing} onClick={handleComplete}>
                  {completing ? "İşleniyor…" : "Teslim Edildi / Alındı"}
                </Button>
              ) : null}
            </div>

            <div className={styles.contentSection}>
              <div className={styles.sectionHeading}>
                <span className={styles.sectionIcon} aria-hidden="true"><PackageOpen size={18} /></span>
                <div>
                  <h3 className={styles.sectionTitle}>Ürünler ve İade Adetleri</h3>
                  <p className={styles.sectionDescription}>İade edilecek her ürün için adet seçin.</p>
                </div>
              </div>
              <div className={styles.itemsTable}>
                <Table>
                  <thead>
                    <tr>
                      <th>Ürün</th>
                      <th className={styles.numericCell}>Birim Fiyat</th>
                      <th className={styles.numericCell}>Sipariş Adedi</th>
                      <th className={styles.numericCell}>İade Edilen</th>
                      <th className={styles.numericCell}>Kalan</th>
                      <th className={styles.quantityCell}>İade Adedi</th>
                    </tr>
                  </thead>
                  <tbody>
                    {order.items.map((item) => (
                      <tr key={item.id}>
                        <td>
                          <p className={styles.itemName} title={item.productName}>{item.productName}</p>
                          {item.rejectedQuantity > 0 ? <p className={styles.itemMeta}>{item.rejectedQuantity} adet reddedildi</p> : null}
                          {item.remainingRefundableQuantity === 0 ? <p className={styles.refundedMeta}>Tamamı iade edildi</p> : null}
                        </td>
                        <td className={styles.numericCell}>{formatPriceMinorUnits(item.unitPriceMinorUnits)}</td>
                        <td className={styles.numericCell}>{item.orderedQuantity}</td>
                        <td className={styles.numericCell}>{item.refundedQuantity}</td>
                        <td className={styles.numericCell}>{item.remainingRefundableQuantity}</td>
                        <td className={styles.quantityCell}>
                          <input
                            type="number"
                            className={styles.quantityInput}
                            min={0}
                            max={item.remainingRefundableQuantity}
                            value={quantityInputs[item.id] ?? 0}
                            disabled={item.remainingRefundableQuantity === 0 || submitting}
                            onChange={(event) => {
                              const parsed = Number(event.target.value);
                              const quantity = Number.isFinite(parsed)
                                ? Math.min(item.remainingRefundableQuantity, Math.max(0, Math.floor(parsed)))
                                : 0;
                              setQuantityInputs((current) => ({ ...current, [item.id]: quantity }));
                            }}
                            aria-label={`${item.productName} için iade adedi`}
                          />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              </div>
            </div>

            {order.refunds.length > 0 ? (
              <div className={styles.contentSection}>
                <div className={styles.sectionHeading}>
                  <span className={styles.sectionIcon} aria-hidden="true"><RotateCcw size={18} /></span>
                  <div>
                    <h3 className={styles.sectionTitle}>Geçmiş İadeler</h3>
                    <p className={styles.sectionDescription}>Bu sipariş için oluşturulan iade işlemleri ve güncel durumları.</p>
                  </div>
                </div>
                <div className={styles.refundList}>
                  {order.refunds.map((refund) => (
                    <div key={refund.refundId} className={styles.refundEntry}>
                      <div className={styles.refundHistoryInfo}>
                        <span>{refundDateFormatter.format(new Date(refund.createdAt))}</span>
                        <p>
                          {refund.items.map((refundItem) => {
                            const productName = order.items.find((item) => item.id === refundItem.orderItemId)?.productName ?? "Ürün";
                            return `${productName} × ${refundItem.refundedQuantity}`;
                          }).join(" · ")}
                        </p>
                      </div>
                      <div className={styles.refundHistoryResult}>
                        <Badge tone={refundStatusTone(refund.status)}>{REFUND_STATUS_LABELS[refund.status]}</Badge>
                        <strong>{formatPriceMinorUnits(refund.totalAmountMinorUnits)}</strong>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ) : null}

            <div className={styles.refundAction}>
              <div className={styles.refundWarning}>
                <span className={styles.warningIcon} aria-hidden="true"><AlertTriangle size={18} /></span>
                <div>
                  <h3 className={styles.sectionTitle}>İade İşlemi</h3>
                  <p className={styles.sectionDescription}>
                    {fullyRefunded
                      ? "Bu sipariş tamamen iade edildi."
                      : "Seçilen adetler için iade başlatılır. Bu işlem onaylandıktan sonra geri alınamaz."}
                  </p>
                </div>
              </div>
              <Button className={styles.refundButton} size="lg" variant="danger" disabled={submitting || fullyRefunded} onClick={handleRequestRefund}>
                <RotateCcw size={18} aria-hidden="true" />
                {submitting ? "İşleniyor…" : "İade Başlat"}
              </Button>
            </div>
          </section>
        ) : null}
      </main>

      {confirmingRefundQuantity !== null ? (
        <ConfirmDialog
          title="İadeyi Başlat"
          message={`#${order?.orderNumber} numaralı sipariş için ${confirmingRefundQuantity} adet ürün iade edilecek. Bu işlem geri alınamaz.`}
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
