"use client";

import { useState } from "react";
import { ArrowDown, ArrowUp, Pause, Pencil, Play, SlidersHorizontal, Trash2 } from "lucide-react";
import {
  updateProductDetails,
  upsertBranchProduct,
  type BranchProductAdmin,
  type ProductAdmin,
} from "@/lib/api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { useToast } from "@/components/ui/ToastProvider";
import ProductFormFields, {
  ALLERGEN_LABELS,
  productFormValuesFromProduct,
  type ProductFormValues,
} from "./ProductFormFields";
import OptionGroupsSection from "./OptionGroupsSection";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";
import menuStyles from "../menu.module.css";

type Props = {
  product: ProductAdmin;
  branchProduct: BranchProductAdmin | undefined;
  onProductUpdated: (product: ProductAdmin) => void;
  onBranchProductUpdated: (branchProduct: BranchProductAdmin) => void;
  canMoveUp: boolean;
  canMoveDown: boolean;
  reorderDisabled: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
  onRequestDelete: () => void;
};

type Panel = "none" | "edit" | "options";

/** Menü Yönetimi'nde tek bir ürün satırı: müsaitlik/aktiflik toggle'ları + genişleyen düzenle/ata panelleri. */
export default function ProductRow({
  product,
  branchProduct,
  onProductUpdated,
  onBranchProductUpdated,
  canMoveUp,
  canMoveDown,
  reorderDisabled,
  onMoveUp,
  onMoveDown,
  onRequestDelete,
}: Props) {
  const { showToast } = useToast();
  const [panel, setPanel] = useState<Panel>("none");
  const [busy, setBusy] = useState(false);

  const [formValues, setFormValues] = useState<ProductFormValues>(() => productFormValuesFromProduct(product));

  const isAvailable = branchProduct?.availability === "AVAILABLE";

  function openEdit() {
    setFormValues(productFormValuesFromProduct(product));
    setPanel(panel === "edit" ? "none" : "edit");
  }

  function openOptions() {
    setPanel(panel === "options" ? "none" : "options");
  }

  async function handleToggleAvailability() {
    setBusy(true);
    try {
      const nextAvailability = isAvailable ? "UNAVAILABLE" : "AVAILABLE";
      const updated = await upsertBranchProduct(product.id, nextAvailability);
      onBranchProductUpdated(updated);
    } catch {
      showToast("Şube ürün durumu güncellenemedi.", "error");
    } finally {
      setBusy(false);
    }
  }

  async function handleSaveDetails() {
    if (!formValues.name.trim()) {
      showToast("Ürün adı zorunludur.", "error");
      return;
    }
    const priceMinorUnits = Math.round(Number(formValues.price.replace(",", ".")) * 100);
    if (formValues.price.trim() === "" || !Number.isFinite(priceMinorUnits) || priceMinorUnits < 0) {
      showToast("Geçerli bir fiyat girin.", "error");
      return;
    }
    const prepTimeValue = formValues.preparationMinutes.trim() === "" ? null : Number(formValues.preparationMinutes);
    if (prepTimeValue !== null && (!Number.isInteger(prepTimeValue) || prepTimeValue < 0)) {
      showToast("Geçerli bir hazırlık süresi girin.", "error");
      return;
    }
    setBusy(true);
    try {
      const updated = await updateProductDetails(product.id, {
        name: formValues.name.trim(),
        description: formValues.description.trim() || null,
        basePriceMinorUnits: priceMinorUnits,
        active: product.active,
        estimatedPreparationMinutes: prepTimeValue,
        allergens: formValues.allergens,
        imageUrl: formValues.imageUrl,
      });
      onProductUpdated(updated);
      setPanel("none");
      showToast("Ürün detayları güncellendi.", "success");
    } catch {
      showToast("Ürün detayları güncellenemedi.", "error");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <tr>
        <td className={tableStyles.primary}>{product.name}</td>
        <td className={tableStyles.muted}>
          {(product.basePriceMinorUnits / 100).toFixed(2)} ₺
          {product.estimatedPreparationMinutes != null ? ` · ~${product.estimatedPreparationMinutes} dk` : ""}
        </td>
        <td className={tableStyles.muted}>{product.allergens.length > 0 ? product.allergens.map((a) => ALLERGEN_LABELS[a]).join(", ") : "—"}</td>
        <td>
          {!product.active ? <Badge tone="danger">Pasif</Badge> : null}{" "}
          <Badge tone={isAvailable ? "neutral" : "danger"}>{isAvailable ? "Şubede satışta" : "Şubede yok"}</Badge>
        </td>
        <td>
          <div className={tableStyles.actions}>
            <div className={menuStyles.rowActionGroup}>
              <Button size="sm" variant="accent" disabled={busy} onClick={openOptions}>
                <SlidersHorizontal size={13} aria-hidden="true" /> {panel === "options" ? "Vazgeç" : "Seçenekler"}
              </Button>
              <Button size="sm" variant="secondary" disabled={busy} onClick={openEdit}>
                <Pencil size={13} aria-hidden="true" /> {panel === "edit" ? "Vazgeç" : "Düzenle"}
              </Button>
              <Button size="sm" variant="warning" disabled={busy} onClick={handleToggleAvailability}>
                {isAvailable ? <Pause size={13} aria-hidden="true" /> : <Play size={13} aria-hidden="true" />}{" "}
                {isAvailable ? "Satıştan Kaldır" : "Satışa Aç"}
              </Button>
              <Button size="sm" variant="danger" disabled={busy} onClick={onRequestDelete} aria-label={`${product.name} ürününü sil`}>
                <Trash2 size={13} aria-hidden="true" /> Sil
              </Button>
            </div>
            <div className={menuStyles.reorderGroup}>
              <button
                type="button"
                className={menuStyles.rowIconButton}
                disabled={!canMoveUp || reorderDisabled}
                onClick={onMoveUp}
                aria-label={`${product.name} ürününü yukarı taşı`}
                title="Yukarı taşı"
              >
                <ArrowUp size={14} aria-hidden="true" />
              </button>
              <button
                type="button"
                className={menuStyles.rowIconButton}
                disabled={!canMoveDown || reorderDisabled}
                onClick={onMoveDown}
                aria-label={`${product.name} ürününü aşağı taşı`}
                title="Aşağı taşı"
              >
                <ArrowDown size={14} aria-hidden="true" />
              </button>
            </div>
          </div>
        </td>
      </tr>

      {panel === "edit" ? (
        <tr className={tableStyles.expandedRow}>
          <td colSpan={5}>
            <div className={styles.section}>
              <ProductFormFields values={formValues} onChange={setFormValues} />
              <div className={menuStyles.productFormActions}>
                <Button size="md" variant="secondary" disabled={busy} onClick={() => setPanel("none")}>
                  Vazgeç
                </Button>
                <Button size="md" disabled={busy} onClick={handleSaveDetails}>
                  {busy ? "Kaydediliyor…" : "Kaydet"}
                </Button>
              </div>
            </div>
          </td>
        </tr>
      ) : null}

      {panel === "options" ? (
        <tr className={tableStyles.expandedRow}>
          <td colSpan={5}>
            <div className={styles.section}>
              <OptionGroupsSection productId={product.id} />
            </div>
          </td>
        </tr>
      ) : null}

    </>
  );
}
