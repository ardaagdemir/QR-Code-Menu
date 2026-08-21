"use client";

import { useState } from "react";
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
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";
import menuStyles from "../menu.module.css";

type Props = {
  product: ProductAdmin;
  branchProduct: BranchProductAdmin | undefined;
  onProductUpdated: (product: ProductAdmin) => void;
  onBranchProductUpdated: (branchProduct: BranchProductAdmin) => void;
};

type Panel = "none" | "edit";

/** Menü Yönetimi'nde tek bir ürün satırı: müsaitlik/aktiflik toggle'ları + genişleyen düzenle/ata panelleri. */
export default function ProductRow({ product, branchProduct, onProductUpdated, onBranchProductUpdated }: Props) {
  const { showToast } = useToast();
  const [panel, setPanel] = useState<Panel>("none");
  const [busy, setBusy] = useState(false);

  const [formValues, setFormValues] = useState<ProductFormValues>(() => productFormValuesFromProduct(product));

  const isAvailable = branchProduct?.availability === "AVAILABLE";

  function openEdit() {
    setFormValues(productFormValuesFromProduct(product));
    setPanel(panel === "edit" ? "none" : "edit");
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
    const prepTimeValue = formValues.preparationMinutes.trim() === "" ? null : Number(formValues.preparationMinutes);
    if (prepTimeValue !== null && (!Number.isInteger(prepTimeValue) || prepTimeValue < 0)) {
      showToast("Geçerli bir hazırlık süresi girin.", "error");
      return;
    }
    setBusy(true);
    try {
      const updated = await updateProductDetails(product.id, product.active, prepTimeValue, formValues.allergens, formValues.imageUrl);
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
          {(product.basePriceMinorUnits / 100).toFixed(2)} ₺ · KDV %{product.taxRatePercent}
          {product.estimatedPreparationMinutes != null ? ` · ~${product.estimatedPreparationMinutes} dk` : ""}
        </td>
        <td className={tableStyles.muted}>{product.allergens.length > 0 ? product.allergens.map((a) => ALLERGEN_LABELS[a]).join(", ") : "—"}</td>
        <td>
          {!product.active ? <Badge tone="danger">Pasif</Badge> : null}{" "}
          <Badge tone={isAvailable ? "neutral" : "danger"}>{isAvailable ? "Şubede satışta" : "Şubede yok"}</Badge>
        </td>
        <td>
          <div className={tableStyles.actions}>
            <Button size="md" variant="secondary" disabled={busy} onClick={handleToggleAvailability}>
              {isAvailable ? "Satıştan Kaldır" : "Satışa Aç"}
            </Button>
            <Button size="md" variant="ghost" disabled={busy} onClick={openEdit}>
              {panel === "edit" ? "Vazgeç" : "Düzenle"}
            </Button>
          </div>
        </td>
      </tr>

      {panel === "edit" ? (
        <tr className={tableStyles.expandedRow}>
          <td colSpan={5}>
            <div className={styles.section}>
              <ProductFormFields values={formValues} onChange={setFormValues} mode="edit" />
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

    </>
  );
}
