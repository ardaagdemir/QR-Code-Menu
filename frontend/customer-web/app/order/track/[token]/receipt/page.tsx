"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ApiError, formatPriceMinorUnits, getReceipt, type Receipt } from "@/lib/api";
import Button from "@/components/ui/Button";
import ErrorState from "@/components/ui/ErrorState";
import Skeleton from "@/components/ui/Skeleton";
import styles from "./page.module.css";

type LoadState = { status: "loading" } | { status: "error"; message: string } | { status: "ready"; receipt: Receipt };

function formatDate(iso: string): string {
  return new Intl.DateTimeFormat("tr-TR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(iso));
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]!);
}

/** Standalone .html snapshot the browser can save/open/print later - no PDF library needed. */
function buildReceiptHtml(receipt: Receipt): string {
  const itemRows = receipt.items
    .map(
      (item) =>
        `<tr><td>${escapeHtml(item.productName)}</td><td class="numeric">${item.quantity}</td><td class="numeric">${formatPriceMinorUnits(item.lineTotalMinorUnits)}</td></tr>`,
    )
    .join("");

  const refundRow =
    receipt.totalRefundedMinorUnits > 0
      ? `<div class="row refund"><span>İade Edilen</span><span>-${formatPriceMinorUnits(receipt.totalRefundedMinorUnits)}</span></div>`
      : "";

  const refundsSection =
    receipt.refunds.length > 0
      ? `<div class="refunds"><p class="refundsTitle">İade Geçmişi</p>${receipt.refunds
          .map(
            (refund) =>
              `<div class="row"><span>${formatDate(refund.createdAt)}</span><span>${formatPriceMinorUnits(refund.totalAmountMinorUnits)}</span></div>`,
          )
          .join("")}</div>`
      : "";

  return `<!doctype html>
<html lang="tr">
<head>
<meta charset="utf-8" />
<title>Makbuz - ${escapeHtml(receipt.businessName)}</title>
<style>
body { font-family: system-ui, sans-serif; max-width: 420px; margin: 2rem auto; color: #1a1a1a; }
.notice { text-align: center; font-size: 0.75rem; color: #666; margin-bottom: 1rem; }
.header { text-align: center; padding-bottom: 1rem; border-bottom: 1px dashed #999; }
.businessName { font-size: 1.125rem; font-weight: 700; }
.branchName { margin-top: 0.25rem; font-size: 0.875rem; color: #666; }
.meta { margin-top: 0.75rem; font-size: 0.75rem; color: #666; }
table { width: 100%; margin-top: 1.5rem; border-collapse: collapse; font-size: 0.875rem; }
th { text-align: left; font-size: 0.75rem; color: #666; font-weight: 400; padding-bottom: 0.5rem; border-bottom: 1px solid #ddd; }
td { padding: 0.5rem 0; border-bottom: 1px solid #ddd; }
.numeric { text-align: right; white-space: nowrap; }
.summary { margin-top: 1.5rem; display: flex; flex-direction: column; gap: 0.5rem; font-size: 0.875rem; }
.row { display: flex; justify-content: space-between; }
.total { font-weight: 700; font-size: 1rem; padding-top: 0.5rem; border-top: 1px dashed #999; }
.refund { color: #c0392b; }
.refunds { margin-top: 1.5rem; padding-top: 1rem; border-top: 1px dashed #999; }
.refundsTitle { font-size: 0.875rem; font-weight: 600; margin-bottom: 0.75rem; }
</style>
</head>
<body>
<p class="notice">Bu belge yasal bir fatura değildir.</p>
<div class="header">
<p class="businessName">${escapeHtml(receipt.businessName)}</p>
<p class="branchName">${escapeHtml(receipt.branchName)}</p>
<p class="meta">${receipt.orderNumber !== null ? `Sipariş No: #${receipt.orderNumber} · ` : ""}${formatDate(receipt.orderCreatedAt)}</p>
</div>
<table>
<thead><tr><th>Ürün</th><th class="numeric">Adet</th><th class="numeric">Tutar</th></tr></thead>
<tbody>${itemRows}</tbody>
</table>
<div class="summary">
<div class="row"><span>Ödenen Tutar</span><span>${formatPriceMinorUnits(receipt.totalMinorUnits)}</span></div>
${refundRow}
<div class="row total"><span>Net Ödenen</span><span>${formatPriceMinorUnits(receipt.netPaidMinorUnits)}</span></div>
</div>
${refundsSection}
</body>
</html>`;
}

function downloadReceipt(receipt: Receipt): void {
  const html = buildReceiptHtml(receipt);
  const blob = new Blob([html], { type: "text/html" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `makbuz${receipt.orderNumber !== null ? `-${receipt.orderNumber}` : ""}.html`;
  anchor.click();
  URL.revokeObjectURL(url);
}

/**
 * Section 4, customer-web screen #10: downloadable receipt - "indirilebilir HTML, yasal
 * fatura değildir" (Section 7/12: no PDF library, no legal invoice fields - download is a
 * standalone .html snapshot, not a generated PDF). Static snapshot fetched once - unlike
 * the tracking page, a paid order's receipt contents (items, totals) never change after
 * payment, only refunds can be added later, and re-visiting this page after a refund
 * already shows the latest data via a fresh fetch.
 */
export default function ReceiptPage() {
  const params = useParams<{ token: string }>();
  const token = params.token;
  const router = useRouter();
  const [state, setState] = useState<LoadState>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const receipt = await getReceipt(token);
        if (!cancelled) {
          setState({ status: "ready", receipt });
        }
      } catch (error) {
        if (cancelled) {
          return;
        }
        const message =
          error instanceof ApiError && (error.status === 404 || error.status === 400)
            ? "Bu sipariş için henüz bir makbuz bulunmuyor."
            : "Makbuz yüklenirken bir sorun oluştu.";
        setState({ status: "error", message });
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [token]);

  const backLink = (
    <button type="button" className={styles.backLink} onClick={() => router.back()}>
      ← Geri
    </button>
  );

  if (state.status === "loading") {
    return (
      <main className={styles.page}>
        {backLink}
        <Skeleton height="4rem" />
        <Skeleton height="8rem" />
      </main>
    );
  }

  if (state.status === "error") {
    return (
      <main className={styles.page}>
        {backLink}
        <div className={styles.centeredState}>
          <ErrorState title="Makbuz bulunamadı" message={state.message} />
        </div>
      </main>
    );
  }

  const { receipt } = state;

  return (
    <main className={styles.page}>
      {backLink}
      <p className={styles.notLegalNotice}>Bu belge yasal bir fatura değildir.</p>

      <div className={styles.header}>
        <p className={styles.businessName}>{receipt.businessName}</p>
        <p className={styles.branchName}>{receipt.branchName}</p>
        <p className={styles.meta}>
          {receipt.orderNumber !== null ? `Sipariş No: #${receipt.orderNumber} · ` : ""}
          {formatDate(receipt.orderCreatedAt)}
        </p>
      </div>

      <table className={styles.itemTable}>
        <thead>
          <tr>
            <th>Ürün</th>
            <th className={styles.numeric}>Adet</th>
            <th className={styles.numeric}>Tutar</th>
          </tr>
        </thead>
        <tbody>
          {receipt.items.map((item, index) => (
            <tr key={index}>
              <td>{item.productName}</td>
              <td className={styles.numeric}>{item.quantity}</td>
              <td className={styles.numeric}>{formatPriceMinorUnits(item.lineTotalMinorUnits)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className={styles.summary}>
        <div className={styles.summaryRow}>
          <span>Ödenen Tutar</span>
          <span>{formatPriceMinorUnits(receipt.totalMinorUnits)}</span>
        </div>
        {receipt.totalRefundedMinorUnits > 0 ? (
          <div className={`${styles.summaryRow} ${styles.refundRow}`}>
            <span>İade Edilen</span>
            <span>-{formatPriceMinorUnits(receipt.totalRefundedMinorUnits)}</span>
          </div>
        ) : null}
        <div className={`${styles.summaryRow} ${styles.summaryTotal}`}>
          <span>Net Ödenen</span>
          <span>{formatPriceMinorUnits(receipt.netPaidMinorUnits)}</span>
        </div>
      </div>

      {receipt.refunds.length > 0 ? (
        <div className={styles.refundsSection}>
          <p className={styles.refundsTitle}>İade Geçmişi</p>
          {receipt.refunds.map((refund, index) => (
            <div key={index} className={styles.refundEntry}>
              <span>{formatDate(refund.createdAt)}</span>
              <span>{formatPriceMinorUnits(refund.totalAmountMinorUnits)}</span>
            </div>
          ))}
        </div>
      ) : null}

      <Button className={styles.downloadButton} size="lg" onClick={() => downloadReceipt(receipt)}>
        İndir
      </Button>
    </main>
  );
}
