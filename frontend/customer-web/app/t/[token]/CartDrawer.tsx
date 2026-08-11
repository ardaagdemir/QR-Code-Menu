"use client";

import { useState } from "react";
import { formatPriceMinorUnits, type Cart } from "@/lib/api";
import BottomSheet from "@/components/ui/BottomSheet";
import Button from "@/components/ui/Button";
import styles from "./CartDrawer.module.css";

type Props = {
  cart: Cart;
  onRemoveItem: (orderItemId: string) => void;
  removingItemId: string | null;
  onCheckout: () => void;
  checkoutSubmitting: boolean;
  checkoutError: string | null;
};

export default function CartDrawer({ cart, onRemoveItem, removingItemId, onCheckout, checkoutSubmitting, checkoutError }: Props) {
  const [open, setOpen] = useState(false);
  const itemCount = cart.items.reduce((sum, item) => sum + item.quantity, 0);

  if (cart.items.length === 0) {
    return null;
  }

  return (
    <>
      <button type="button" className={styles.bar} onClick={() => setOpen(true)}>
        <span className={styles.count}>{itemCount} ürün</span>
        <span className={styles.total}>{formatPriceMinorUnits(cart.totalMinorUnits)}</span>
        <span className={styles.cta}>Sepetim</span>
      </button>

      {open ? (
        <BottomSheet onClose={() => setOpen(false)} labelledBy="cart-drawer-title">
          <h2 id="cart-drawer-title" className={styles.title}>
            Sepetim
          </h2>
          <ul className={styles.itemList}>
            {cart.items.map((item) => (
              <li key={item.id} className={styles.item}>
                <div className={styles.itemInfo}>
                  <p className={styles.itemName}>
                    {item.quantity}× {item.productName}
                  </p>
                  {item.options.length > 0 ? (
                    <p className={styles.itemOptions}>{item.options.map((option) => option.name).join(", ")}</p>
                  ) : null}
                </div>
                <div className={styles.itemRight}>
                  <span className={styles.itemPrice}>{formatPriceMinorUnits(item.lineTotalMinorUnits)}</span>
                  <button
                    type="button"
                    className={styles.removeButton}
                    disabled={removingItemId === item.id}
                    onClick={() => onRemoveItem(item.id)}
                  >
                    Kaldır
                  </button>
                </div>
              </li>
            ))}
          </ul>
          <div className={styles.summary}>
            <span>Toplam</span>
            <span className={styles.summaryTotal}>{formatPriceMinorUnits(cart.totalMinorUnits)}</span>
          </div>

          {checkoutError ? <p className={styles.checkoutError}>{checkoutError}</p> : null}
          <Button size="lg" className={styles.checkoutButton} disabled={checkoutSubmitting} onClick={onCheckout}>
            {checkoutSubmitting ? "Hazırlanıyor…" : "Ödemeye Geç"}
          </Button>

          <button type="button" className={styles.closeLink} onClick={() => setOpen(false)}>
            Menüye dön
          </button>
        </BottomSheet>
      ) : null}
    </>
  );
}
