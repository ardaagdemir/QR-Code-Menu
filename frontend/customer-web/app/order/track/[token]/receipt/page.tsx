"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ApiError, formatPriceMinorUnits, getReceipt, type Receipt } from "@/lib/api";
import Button from "@/components/ui/Button";
import ErrorState from "@/components/ui/ErrorState";
import Skeleton from "@/components/ui/Skeleton";
import styles from "./page.module.css";

type LoadState = { status: "loading" } | { status: "error"; message: string } | { status: "ready"; receipt: Receipt };

function formatDate(iso: string): string {
  return new Intl.DateTimeFormat("tr-TR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(iso));
}

/**
 * Section 4, customer-web screen #10: printable receipt - "yazdırılabilir HTML, yasal
 * fatura değildir" (Section 7/12: no PDF library, no legal invoice fields). Static
 * snapshot fetched once - unlike the tracking page, a paid order's receipt contents
 * (items, totals) never change after payment, only refunds can be added later, and
 * re-visiting this page after a refund already shows the latest data via a fresh fetch.
 */
export default function ReceiptPage() {
  const params = useParams<{ token: string }>();
  const token = params.token;
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

  if (state.status === "loading") {
    return (
      <main className={styles.page}>
        <Skeleton height="4rem" />
        <Skeleton height="8rem" />
      </main>
    );
  }

  if (state.status === "error") {
    return (
      <main className={styles.page}>
        <div className={styles.centeredState}>
          <ErrorState title="Makbuz bulunamadı" message={state.message} />
        </div>
      </main>
    );
  }

  const { receipt } = state;

  return (
    <main className={styles.page}>
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
          {receipt.refunds.map((refund) => (
            <div key={refund.refundId} className={styles.refundEntry}>
              <span>{formatDate(refund.createdAt)}</span>
              <span>{formatPriceMinorUnits(refund.totalAmountMinorUnits)}</span>
            </div>
          ))}
        </div>
      ) : null}

      <Button className={styles.printButton} size="lg" onClick={() => window.print()}>
        Yazdır
      </Button>
    </main>
  );
}
