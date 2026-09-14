"use client";

import { useMemo, useState } from "react";
import { formatPriceMinorUnits, type MenuOptionGroup, type MenuProduct } from "@/lib/api";
import BottomSheet from "@/components/ui/BottomSheet";
import Button from "@/components/ui/Button";
import DishPlaceholderIcon from "@/components/ui/DishPlaceholderIcon";
import IconButton from "@/components/ui/IconButton";
import QuantityStepper from "@/components/ui/QuantityStepper";
import styles from "./ProductOptionsSheet.module.css";

type Props = {
  product: MenuProduct;
  onClose: () => void;
  onConfirm: (selectedOptionIds: string[], quantity: number) => void;
  submitting: boolean;
  errorMessage: string | null;
};

export default function ProductOptionsSheet({ product, onClose, onConfirm, submitting, errorMessage }: Props) {
  const [selectedByGroup, setSelectedByGroup] = useState<Record<string, string[]>>({});
  const [quantity, setQuantity] = useState(1);
  const [imageFailed, setImageFailed] = useState(false);

  const selectedOptionIds = useMemo(() => Object.values(selectedByGroup).flat(), [selectedByGroup]);

  const missingRequiredGroup = product.optionGroups.some(
    (group) => group.selectionType === "SINGLE" && (selectedByGroup[group.id]?.length ?? 0) !== 1,
  );

  const allOptions = product.optionGroups.flatMap((group) => group.options);
  const unitPriceMinorUnits =
    product.priceMinorUnits +
    allOptions
      .filter((option) => selectedOptionIds.includes(option.id))
      .reduce((sum, option) => sum + option.priceDeltaMinorUnits, 0);

  function selectSingle(group: MenuOptionGroup, optionId: string) {
    setSelectedByGroup((prev) => ({ ...prev, [group.id]: [optionId] }));
  }

  function toggleMultiple(group: MenuOptionGroup, optionId: string) {
    setSelectedByGroup((prev) => {
      const current = prev[group.id] ?? [];
      const next = current.includes(optionId) ? current.filter((id) => id !== optionId) : [...current, optionId];
      return { ...prev, [group.id]: next };
    });
  }

  const showImage = Boolean(product.imageUrl) && !imageFailed;

  return (
    <BottomSheet onClose={onClose} labelledBy="product-sheet-title" className={styles.sheet}>
      <div className={styles.layout}>
        <div className={styles.media}>
          {showImage ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={product.imageUrl ?? undefined} alt="" className={styles.image} onError={() => setImageFailed(true)} />
          ) : (
            <div className={styles.imagePlaceholder} aria-hidden="true">
              <DishPlaceholderIcon size={40} />
            </div>
          )}
          <IconButton
            aria-label="Kapat"
            size="sm"
            variant="secondary"
            className={styles.closeButton}
            onClick={onClose}
          >
            ×
          </IconButton>
        </div>

        <div className={styles.details}>
          <h2 id="product-sheet-title" className={styles.title}>
            {product.name}
          </h2>
          {product.description ? <p className={styles.description}>{product.description}</p> : null}
          <p className={styles.price}>{formatPriceMinorUnits(unitPriceMinorUnits)}</p>

          {product.optionGroups.map((group) => (
            <fieldset key={group.id} className={styles.group}>
              <legend className={styles.groupLegend}>
                {group.name}{" "}
                <span className={styles.groupHint}>{group.selectionType === "SINGLE" ? "Zorunlu · 1 seçim" : "Opsiyonel"}</span>
              </legend>
              {group.options.map((option) => (
                <label key={option.id} className={styles.optionRow}>
                  <input
                    type={group.selectionType === "SINGLE" ? "radio" : "checkbox"}
                    name={group.id}
                    className={styles.optionInput}
                    checked={(selectedByGroup[group.id] ?? []).includes(option.id)}
                    onChange={() =>
                      group.selectionType === "SINGLE" ? selectSingle(group, option.id) : toggleMultiple(group, option.id)
                    }
                  />
                  <span className={styles.optionName}>{option.name}</span>
                  {option.priceDeltaMinorUnits !== 0 ? (
                    <span className={styles.optionDelta}>+{formatPriceMinorUnits(option.priceDeltaMinorUnits)}</span>
                  ) : null}
                </label>
              ))}
            </fieldset>
          ))}

          <div className={styles.quantityRow}>
            <span className={styles.quantityLabel}>Adet</span>
            <QuantityStepper value={quantity} onChange={setQuantity} />
          </div>

          {errorMessage ? <p className={styles.error}>{errorMessage}</p> : null}

          <div className={styles.actions}>
            <Button variant="secondary" size="lg" onClick={onClose} disabled={submitting}>
              Vazgeç
            </Button>
            <Button
              size="lg"
              className={styles.confirmButton}
              disabled={missingRequiredGroup || submitting}
              onClick={() => onConfirm(selectedOptionIds, quantity)}
            >
              Sepete Ekle · {formatPriceMinorUnits(unitPriceMinorUnits * quantity)}
            </Button>
          </div>
        </div>
      </div>
    </BottomSheet>
  );
}
