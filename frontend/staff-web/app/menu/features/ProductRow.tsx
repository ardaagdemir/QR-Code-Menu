"use client";

import { useState } from "react";
import {
  ALLERGENS,
  updateProductDetails,
  uploadProductImage,
  upsertBranchProduct,
  type Allergen,
  type BranchProductAdmin,
  type ProductAdmin,
} from "@/lib/api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import FileUploadField from "@/components/ui/FileUploadField";
import { useToast } from "@/components/ui/ToastProvider";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";
import menuStyles from "../menu.module.css";

const ALLERGEN_LABELS: Record<Allergen, string> = {
  GLUTEN: "Gluten",
  CRUSTACEANS: "Kabuklu deniz ürünleri",
  EGGS: "Yumurta",
  FISH: "Balık",
  PEANUTS: "Yer fıstığı",
  SOYBEANS: "Soya",
  MILK: "Süt",
  TREE_NUTS: "Kuruyemiş",
  CELERY: "Kereviz",
  MUSTARD: "Hardal",
  SESAME: "Susam",
  SULPHITES: "Sülfit",
  LUPIN: "Acı bakla",
  MOLLUSCS: "Yumuşakçalar",
};

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

  const [prepTime, setPrepTime] = useState(product.estimatedPreparationMinutes?.toString() ?? "");
  const [allergens, setAllergens] = useState<Set<Allergen>>(new Set(product.allergens));
  const [imageUrl, setImageUrl] = useState<string | null>(product.imageUrl);


  const isAvailable = branchProduct?.availability === "AVAILABLE";

  function openEdit() {
    setPrepTime(product.estimatedPreparationMinutes?.toString() ?? "");
    setAllergens(new Set(product.allergens));
    setImageUrl(product.imageUrl);
    setPanel(panel === "edit" ? "none" : "edit");
  }

  function toggleAllergen(allergen: Allergen) {
    setAllergens((current) => {
      const next = new Set(current);
      if (next.has(allergen)) {
        next.delete(allergen);
      } else {
        next.add(allergen);
      }
      return next;
    });
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

  async function handleTogglePassive() {
    setBusy(true);
    try {
      const updated = await updateProductDetails(
        product.id,
        !product.active,
        product.estimatedPreparationMinutes,
        product.allergens,
        product.imageUrl,
      );
      onProductUpdated(updated);
    } catch {
      showToast("Ürün durumu güncellenemedi.", "error");
    } finally {
      setBusy(false);
    }
  }

  async function handleSaveDetails() {
    const prepTimeValue = prepTime.trim() === "" ? null : Number(prepTime);
    if (prepTimeValue !== null && (!Number.isInteger(prepTimeValue) || prepTimeValue < 0)) {
      showToast("Geçerli bir hazırlık süresi girin.", "error");
      return;
    }
    setBusy(true);
    try {
      const updated = await updateProductDetails(product.id, product.active, prepTimeValue, Array.from(allergens), imageUrl);
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
              {isAvailable ? "Kaldır" : "Şubeye Ekle"}
            </Button>
            <Button className={product.active ? menuStyles.dangerAction : undefined} size="md" variant={product.active ? "ghost" : "secondary"} disabled={busy} onClick={handleTogglePassive}>
              {product.active ? "Pasif Yap" : "Aktif Yap"}
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
              <FileUploadField
                label="Ürün görseli"
                hint="JPEG, PNG veya WEBP - en fazla 5MB."
                accept="image/jpeg,image/png,image/webp"
                value={imageUrl}
                onChange={setImageUrl}
                upload={uploadProductImage}
              />
              <FormField label="Hazırlık süresi (dk)">
                {(controlProps) => <Input {...controlProps} inputMode="numeric" value={prepTime} onChange={(event) => setPrepTime(event.target.value)} />}
              </FormField>
              <div className={styles.field}>
                <span className={styles.label}>Alerjenler</span>
                <div className={styles.rowActions}>
                  {ALLERGENS.map((allergen) => (
                    <label key={allergen} className={styles.rowMeta}>
                      <input type="checkbox" checked={allergens.has(allergen)} onChange={() => toggleAllergen(allergen)} /> {ALLERGEN_LABELS[allergen]}
                    </label>
                  ))}
                </div>
              </div>
              <Button size="md" disabled={busy} onClick={handleSaveDetails}>
                Kaydet
              </Button>
            </div>
          </td>
        </tr>
      ) : null}

    </>
  );
}
