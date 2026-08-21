"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ClipboardList, PackageOpen, Search } from "lucide-react";
import {
  ApiError,
  formatPriceMinorUnits,
  getInProgressOrders,
  getOrderHistory,
  getPendingAcceptanceOrders,
  getReadyOrders,
  searchOrderByNumber,
  type OrderControlItem,
  type OrderControlOrder,
  type OrderHistoryOrder,
  type StaffOrderLookup,
} from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import Tabs from "@/components/ui/Tabs";
import Input from "@/components/ui/Input";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Table from "@/components/ui/Table";
import TableSkeleton from "@/components/ui/TableSkeleton";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import Dialog from "@/components/ui/Dialog";
import DateRangePresets, { presetRange, type DateRange } from "@/components/ui/DateRangePresets";
import styles from "./page.module.css";

const ORDER_STATUS_LABELS: Record<string, string> = {
  AWAITING_STORE_ACCEPTANCE: "Onay Bekliyor",
  IN_KITCHEN: "Hazırlanıyor",
  READY: "Teslime Hazır",
  COMPLETED: "Tamamlandı",
  REJECTED_BY_STORE: "Reddedildi",
};

const REFUND_STATUS_LABELS: Record<string, string> = {
  REQUESTED: "Talep Edildi",
  PROCESSING: "İşleniyor",
  COMPLETED: "Tamamlandı",
  FAILED: "Başarısız",
};

function orderStatusTone(status: string): "neutral" | "success" | "danger" | "warning" | "info" {
  if (status === "READY" || status === "COMPLETED") return "success";
  if (status === "REJECTED_BY_STORE") return "danger";
  if (status === "AWAITING_STORE_ACCEPTANCE") return "warning";
  if (status === "IN_KITCHEN") return "info";
  return "neutral";
}

function refundStatusTone(status: string): "neutral" | "success" | "danger" | "warning" {
  if (status === "COMPLETED") return "success";
  if (status === "FAILED") return "danger";
  if (status === "PROCESSING") return "warning";
  return "neutral";
}

const dateFormatter = new Intl.DateTimeFormat("tr-TR", {
  day: "numeric",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

/** Both OrderControlOrder (aktif) and OrderHistoryOrder (tamamlanan/reddedilen/iade) render through this one row shape. */
type OrderRow = {
  orderId: string;
  orderNumber: number | null;
  status: string;
  totalMinorUnits: number;
  tableLabel: string | null;
  timestamp: string;
  rejectionReasonCode: string | null;
  rejectionNote: string | null;
  latestRefundStatus: string | null;
  items: OrderControlItem[];
};

function fromControlOrder(order: OrderControlOrder): OrderRow {
  return {
    orderId: order.orderId,
    orderNumber: order.orderNumber,
    status: order.status,
    totalMinorUnits: order.totalMinorUnits,
    tableLabel: order.tableLabel,
    timestamp: order.statusSince,
    rejectionReasonCode: order.rejectionReasonCode,
    rejectionNote: order.rejectionNote,
    latestRefundStatus: null,
    items: order.items,
  };
}

function fromHistoryOrder(order: OrderHistoryOrder): OrderRow {
  return {
    orderId: order.orderId,
    orderNumber: order.orderNumber,
    status: order.status,
    totalMinorUnits: order.totalMinorUnits,
    tableLabel: order.tableLabel,
    timestamp: order.completedAt ?? order.createdAt,
    rejectionReasonCode: order.rejectionReasonCode,
    rejectionNote: order.rejectionNote,
    latestRefundStatus: order.latestRefundStatus,
    items: order.items,
  };
}

function fromLookup(order: StaffOrderLookup): OrderRow {
  return {
    orderId: order.orderId,
    orderNumber: order.orderNumber,
    status: order.status,
    totalMinorUnits: order.totalMinorUnits,
    tableLabel: null,
    timestamp: order.refunds[order.refunds.length - 1]?.createdAt ?? "",
    rejectionReasonCode: null,
    rejectionNote: null,
    latestRefundStatus: order.refunds[order.refunds.length - 1]?.status ?? null,
    items: order.items.map((item) => ({
      id: item.id,
      productName: item.productName,
      orderedQuantity: item.orderedQuantity,
      acceptedQuantity: item.acceptedQuantity,
      rejectedQuantity: item.rejectedQuantity,
      status: item.status,
      options: [],
    })),
  };
}

type TabId = "active" | "completed" | "rejected" | "refunded";

const TAB_ITEMS: { id: TabId; label: string }[] = [
  { id: "active", label: "Aktif" },
  { id: "completed", label: "Tamamlanan" },
  { id: "rejected", label: "Reddedilen" },
  { id: "refunded", label: "İade" },
];

/**
 * Siparişler ekranı - sipariş görünürlüğü audit'i (2026-08-20): Kasa yalnız aktif
 * operasyon akışını (onay bekleyen/hazırlanan/hazır) gösterir, tamamlanan/reddedilen
 * siparişler oradan düşünce hiçbir yerde görünmezdi. Bu ekran dördü de tek yerde
 * toplar - aktif sekmesi salt-okunur (aksiyonlar Kasa'da kalır), diğer üç sekme
 * backend'in yeni /api/staff/orders/history uç noktasından beslenir. Sipariş no ile
 * arama, tab/tarih filtresinden bağımsız olarak /search'ü (İadeler ekranıyla aynı uç
 * nokta) kullanır.
 */
export default function OrdersPage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<TabId>("active");
  const [range, setRange] = useState<DateRange>(() => presetRange("today"));
  const [rows, setRows] = useState<OrderRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedOrder, setSelectedOrder] = useState<OrderRow | null>(null);

  const [searchInput, setSearchInput] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [searchResult, setSearchResult] = useState<OrderRow | null>(null);

  /**
   * A `.then()` chain, not `async/await` - react-hooks/set-state-in-effect traces
   * `async` function bodies called directly from an effect as unsafe regardless of
   * where the `setState` calls land inside them, same reason app/tables/page.tsx's
   * loadTables is a promise chain rather than an async function.
   */
  const loadTab = useCallback((tab: TabId, tabRange: DateRange) => {
    const request: Promise<OrderRow[]> =
      tab === "active"
        ? Promise.all([getPendingAcceptanceOrders(), getInProgressOrders(), getReadyOrders()]).then(
            ([pending, inProgress, ready]) => [...pending, ...inProgress, ...ready].map(fromControlOrder),
          )
        : tab === "completed"
          ? getOrderHistory(["COMPLETED"], tabRange.from, tabRange.to).then((history) => history.map(fromHistoryOrder))
          : tab === "rejected"
            ? getOrderHistory(["REJECTED_BY_STORE"], tabRange.from, tabRange.to).then((history) => history.map(fromHistoryOrder))
            : getOrderHistory(["COMPLETED", "REJECTED_BY_STORE"], tabRange.from, tabRange.to).then((history) =>
                history.filter((order) => order.latestRefundStatus !== null).map(fromHistoryOrder),
              );

    return request
      .then((result) => {
        setRows(result);
        setError(null);
      })
      .catch((err) => {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          router.replace("/");
          return;
        }
        setError("Siparişler yüklenirken bir sorun oluştu.");
      })
      .finally(() => setLoading(false));
  }, [router]);

  useEffect(() => {
    void loadTab(activeTab, range);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleTabChange(tab: TabId) {
    setActiveTab(tab);
    setLoading(true);
    void loadTab(tab, range);
  }

  function handleRangeChange(nextRange: DateRange) {
    setRange(nextRange);
    setLoading(true);
    void loadTab(activeTab, nextRange);
  }

  async function handleSearch(event: React.FormEvent) {
    event.preventDefault();
    const orderNumber = Number(searchInput);
    if (!Number.isInteger(orderNumber) || orderNumber <= 0) {
      setSearchError("Geçerli bir sipariş numarası girin.");
      return;
    }
    setSearching(true);
    setSearchError(null);
    try {
      const found = await searchOrderByNumber(orderNumber);
      setSearchResult(fromLookup(found));
    } catch (err) {
      setSearchResult(null);
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        router.replace("/");
        return;
      }
      setSearchError(err instanceof ApiError && err.status === 404 ? "Bu numarada bir sipariş bulunamadı." : "Sipariş aranırken bir sorun oluştu.");
    } finally {
      setSearching(false);
    }
  }

  function clearSearch() {
    setSearchInput("");
    setSearchResult(null);
    setSearchError(null);
  }

  const displayedRows = useMemo(() => (searchResult ? [searchResult] : rows), [searchResult, rows]);

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader title="Siparişler" description="Aktif, tamamlanan, reddedilen ve iade edilen siparişleri sipariş numarasıyla görüntüleyin." />

        <section className={styles.searchCard} aria-labelledby="orders-search-title">
          <form className={styles.searchRow} onSubmit={handleSearch}>
            <div className={styles.searchField}>
              <label className={styles.searchLabel} htmlFor="orders-search-input">Sipariş numarası ile ara</label>
              <div className={styles.searchInputWrap}>
                <Search size={17} aria-hidden="true" />
                <Input
                  id="orders-search-input"
                  className={styles.searchInput}
                  placeholder="Örn. 1042"
                  inputMode="numeric"
                  value={searchInput}
                  onChange={(event) => setSearchInput(event.target.value)}
                />
              </div>
            </div>
            <Button type="submit" disabled={searching}>{searching ? "Aranıyor…" : "Ara"}</Button>
            {searchResult ? <Button type="button" variant="secondary" onClick={clearSearch}>Aramayı Temizle</Button> : null}
          </form>
          {searchError ? <p className={styles.searchError} role="alert">{searchError}</p> : null}
        </section>

        {!searchResult ? (
          <>
            <Tabs items={TAB_ITEMS} activeId={activeTab} onChange={(id) => handleTabChange(id as TabId)} ariaLabel="Sipariş durumu" />
            {activeTab !== "active" ? <DateRangePresets value={range} onChange={handleRangeChange} /> : null}
          </>
        ) : null}

        {loading && !searchResult ? (
          <TableSkeleton rows={5} />
        ) : error ? (
          <ErrorState message={error} onRetry={() => { setLoading(true); void loadTab(activeTab, range); }} />
        ) : displayedRows.length === 0 ? (
          <EmptyState
            icon={<ClipboardList size={22} />}
            title="Sipariş bulunamadı"
            description={searchResult === null && searchInput ? undefined : "Seçilen sekme/tarih aralığında sipariş yok."}
          />
        ) : (
          <Table>
            <thead>
              <tr>
                <th>Sipariş No</th>
                <th>Masa</th>
                <th>Durum</th>
                <th>İade</th>
                <th className={styles.numericCell}>Tutar</th>
                <th>Zaman</th>
              </tr>
            </thead>
            <tbody>
              {displayedRows.map((row) => (
                <tr key={row.orderId} className={styles.clickableRow} onClick={() => setSelectedOrder(row)}>
                  <td className={styles.orderNumberCell}>#{row.orderNumber ?? "—"}</td>
                  <td>{row.tableLabel ?? "—"}</td>
                  <td><Badge tone={orderStatusTone(row.status)}>{ORDER_STATUS_LABELS[row.status] ?? row.status}</Badge></td>
                  <td>
                    {row.latestRefundStatus ? (
                      <Badge tone={refundStatusTone(row.latestRefundStatus)}>{REFUND_STATUS_LABELS[row.latestRefundStatus] ?? row.latestRefundStatus}</Badge>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td className={styles.numericCell}>{formatPriceMinorUnits(row.totalMinorUnits)}</td>
                  <td>{row.timestamp ? dateFormatter.format(new Date(row.timestamp)) : "—"}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </main>

      {selectedOrder ? (
        <Dialog onClose={() => setSelectedOrder(null)} labelledBy="order-detail-title" size="md">
          <div className={styles.detail}>
            <div className={styles.detailHeader}>
              <span className={styles.detailIcon} aria-hidden="true"><PackageOpen size={20} /></span>
              <div>
                <h2 id="order-detail-title" className={styles.detailTitle}>Sipariş #{selectedOrder.orderNumber ?? "—"}</h2>
                <div className={styles.detailBadges}>
                  <Badge tone={orderStatusTone(selectedOrder.status)}>{ORDER_STATUS_LABELS[selectedOrder.status] ?? selectedOrder.status}</Badge>
                  {selectedOrder.latestRefundStatus ? (
                    <Badge tone={refundStatusTone(selectedOrder.latestRefundStatus)}>
                      İade: {REFUND_STATUS_LABELS[selectedOrder.latestRefundStatus] ?? selectedOrder.latestRefundStatus}
                    </Badge>
                  ) : null}
                </div>
              </div>
            </div>

            {selectedOrder.tableLabel ? <p className={styles.detailMeta}>Masa: {selectedOrder.tableLabel}</p> : null}
            {selectedOrder.rejectionReasonCode ? (
              <p className={styles.detailMeta}>
                Red nedeni: {selectedOrder.rejectionReasonCode}
                {selectedOrder.rejectionNote ? ` — ${selectedOrder.rejectionNote}` : ""}
              </p>
            ) : null}

            <div className={styles.detailItems}>
              {selectedOrder.items.map((item) => (
                <div key={item.id} className={styles.detailItemRow}>
                  <span>{item.productName} × {item.orderedQuantity}</span>
                </div>
              ))}
            </div>

            <div className={styles.detailTotal}>
              <span>Sipariş toplamı</span>
              <strong>{formatPriceMinorUnits(selectedOrder.totalMinorUnits)}</strong>
            </div>
          </div>
        </Dialog>
      ) : null}
    </AppShell>
  );
}
