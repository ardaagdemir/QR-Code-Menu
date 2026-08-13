"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import {
  ApiError,
  createPaymentIntent,
  formatPriceMinorUnits,
  getOrderTracking,
  getPaymentStatus,
  triggerMockPaymentOutcome,
  type PaymentIntent,
} from "@/lib/api";
import BottomSheet from "@/components/ui/BottomSheet";
import Button from "@/components/ui/Button";
import styles from "./PaymentSheet.module.css";

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

type Props = {
  tableVisitId: string;
  initialIntent: PaymentIntent;
  trackingToken: string | null;
  onClose: () => void;
  onOrderPaid: () => void;
};

type Phase = "awaiting-outcome" | "dispatching" | "succeeded" | "failed" | "error";

/**
 * The mock "hosted payment screen" (Section 2, mock flow step 2) - stands in for a
 * real provider's checkout page. Clicking "Başarılı"/"Başarısız" here does NOT itself
 * finalize anything (Section 2: "frontend'in kendisi bu çağrıyı yapmaz") - it only
 * triggers a separate, asynchronous backend webhook call, so this component polls
 * getPaymentStatus afterward to observe the real outcome once it lands.
 */
export default function PaymentSheet({ tableVisitId, initialIntent, trackingToken, onClose, onOrderPaid }: Props) {
  const [intent, setIntent] = useState(initialIntent);
  const [phase, setPhase] = useState<Phase>("awaiting-outcome");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [orderNumber, setOrderNumber] = useState<number | null>(null);
  const cancelledRef = useRef(false);

  useEffect(() => {
    // Reset on (re-)mount, not just on cleanup - React Strict Mode's dev-only
    // mount->cleanup->mount double-invoke would otherwise leave this stuck at
    // `true` forever, silently killing the poll loop below on a component that
    // is actually still mounted and interactive.
    cancelledRef.current = false;
    return () => {
      cancelledRef.current = true;
    };
  }, []);

  async function pollUntilTerminal(paymentId: string, attemptsLeft: number) {
    for (let remaining = attemptsLeft; remaining > 0; remaining -= 1) {
      await delay(400);
      if (cancelledRef.current) {
        return;
      }
      try {
        const result = await getPaymentStatus(tableVisitId, paymentId);
        if (result.paymentStatus === "SUCCEEDED") {
          setPhase("succeeded");
          // Best-effort - the order number (Section 5) shows once available, but its
          // absence shouldn't block the success screen.
          if (trackingToken) {
            getOrderTracking(trackingToken)
              .then((tracking) => setOrderNumber(tracking.orderNumber))
              .catch(() => {});
          }
          return;
        }
        if (result.paymentStatus === "FAILED") {
          setPhase("failed");
          return;
        }
      } catch {
        // Transient error - keep polling until attempts run out.
      }
    }
    if (!cancelledRef.current) {
      setPhase("error");
      setErrorMessage("Ödeme sonucu doğrulanamadı. Lütfen tekrar deneyin.");
    }
  }

  async function handleSimulate(outcome: "SUCCEEDED" | "FAILED") {
    setPhase("dispatching");
    setErrorMessage(null);
    try {
      await triggerMockPaymentOutcome(tableVisitId, intent.paymentId, outcome);
      await pollUntilTerminal(intent.paymentId, 25);
    } catch (error) {
      setPhase("error");
      setErrorMessage(error instanceof ApiError ? "Simülasyon başlatılamadı." : "Beklenmeyen bir hata oluştu.");
    }
  }

  async function handleRetry() {
    setPhase("dispatching");
    setErrorMessage(null);
    try {
      const newIntent = await createPaymentIntent(tableVisitId);
      setIntent(newIntent);
      setPhase("awaiting-outcome");
    } catch {
      setPhase("error");
      setErrorMessage("Yeni ödeme denemesi başlatılamadı.");
    }
  }

  return (
    <BottomSheet onClose={phase === "succeeded" ? onOrderPaid : onClose} labelledBy="payment-sheet-title">
      <h2 id="payment-sheet-title" className={styles.title}>
        Ödeme
      </h2>
      <p className={styles.amount}>{formatPriceMinorUnits(intent.amountMinorUnits)}</p>

      {phase === "awaiting-outcome" ? (
        <>
          <p className={styles.hint}>
            Bu, gerçek bir ödeme sağlayıcısının test ortamıdır. Gerçek bir kart bilgisi istenmez.
          </p>
          <div className={styles.actions}>
            <Button variant="secondary" size="lg" onClick={onClose}>
              Vazgeç
            </Button>
            <Button size="lg" className={styles.successButton} onClick={() => handleSimulate("SUCCEEDED")}>
              Ödemeyi Onayla
            </Button>
          </div>
          <button type="button" className={styles.failLink} onClick={() => handleSimulate("FAILED")}>
            Ödemeyi başarısız olarak simüle et
          </button>
        </>
      ) : null}

      {phase === "dispatching" ? <p className={styles.status}>İşleniyor…</p> : null}

      {phase === "succeeded" ? (
        <>
          <p className={styles.statusSuccess}>Ödeme başarılı. Siparişiniz işletmeye iletildi, onay bekleniyor.</p>
          {orderNumber !== null ? <p className={styles.orderNumber}>Sipariş No: #{orderNumber}</p> : null}
          {trackingToken ? (
            <Link href={`/order/track/${trackingToken}`} className={styles.trackingLink}>
              Sipariş durumunu takip et
            </Link>
          ) : null}
          <Button size="lg" onClick={onOrderPaid}>
            Tamam
          </Button>
        </>
      ) : null}

      {phase === "failed" ? (
        <>
          <p className={styles.statusFailure}>Ödeme başarısız oldu.</p>
          <div className={styles.actions}>
            <Button variant="secondary" size="lg" onClick={onClose}>
              Vazgeç
            </Button>
            <Button size="lg" className={styles.successButton} onClick={handleRetry}>
              Tekrar Dene
            </Button>
          </div>
        </>
      ) : null}

      {phase === "error" ? (
        <>
          <p className={styles.statusFailure}>{errorMessage}</p>
          <Button size="lg" onClick={handleRetry}>
            Tekrar Dene
          </Button>
        </>
      ) : null}
    </BottomSheet>
  );
}
