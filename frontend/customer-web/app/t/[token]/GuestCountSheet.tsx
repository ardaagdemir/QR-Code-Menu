"use client";

import { useState } from "react";
import BottomSheet from "@/components/ui/BottomSheet";
import Button from "@/components/ui/Button";
import QuantityStepper from "@/components/ui/QuantityStepper";
import styles from "./GuestCountSheet.module.css";

type Props = {
  initialValue: number | null;
  onClose: () => void;
  onConfirm: (guestCount: number) => void;
  submitting: boolean;
  errorMessage: string | null;
};

/** Gap-analysis #17 (Section 13.3): low-friction, skippable, re-editable real-headcount
 * prompt - a real Kaç kişisiniz? sipariş verilmeden önce sorulmaz, arka planda kalır. */
export default function GuestCountSheet({ initialValue, onClose, onConfirm, submitting, errorMessage }: Props) {
  const [guestCount, setGuestCount] = useState(initialValue ?? 2);

  return (
    <BottomSheet onClose={onClose} labelledBy="guest-count-sheet-title">
      <h2 id="guest-count-sheet-title" className={styles.title}>
        Kaç kişisiniz?
      </h2>
      <p className={styles.description}>
        Bu bilgi yalnızca işletmenin ziyaretçi sayısını doğru görebilmesi için kullanılır, isterseniz atlayabilirsiniz.
      </p>

      <div className={styles.stepperRow}>
        <QuantityStepper value={guestCount} onChange={setGuestCount} min={1} max={30} />
      </div>

      {errorMessage ? <p className={styles.error}>{errorMessage}</p> : null}

      <div className={styles.actions}>
        <Button variant="secondary" size="lg" onClick={onClose} disabled={submitting}>
          Atla
        </Button>
        <Button size="lg" className={styles.confirmButton} disabled={submitting} onClick={() => onConfirm(guestCount)}>
          {submitting ? "Kaydediliyor…" : "Kaydet"}
        </Button>
      </div>
    </BottomSheet>
  );
}
