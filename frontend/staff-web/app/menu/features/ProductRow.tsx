"use client";

import { useState } from "react";
import {
  ALLERGENS,
  bulkAssignProductToBranches,
  updateProductDetails,
  upsertBranchProduct,
  type Allergen,
  type Branch,
  type BranchAssignmentTarget,
  type BranchProductAdmin,
  type ProductAdmin,
} from "@/lib/api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import { useToast } from "@/components/ui/ToastProvider";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";

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
  branches: Branch[];
  selectedBranchId: string | null;
  onProductUpdated: (product: ProductAdmin) => void;
  onBranchProductUpdated: (branchProduct: BranchProductAdmin) => void;
};

type Panel = "none" | "edit" | "assign";

/** Menü Yönetimi'nde tek bir ürün satırı: müsaitlik/aktiflik toggle'ları + genişleyen düzenle/ata panelleri. */
export default function ProductRow({ product, branchProduct, branches, selectedBranchId, onProductUpdated, onBranchProductUpdated }: Props) {
  const { showToast } = useToast();
  const [panel, setPanel] = useState<Panel>("none");
  const [busy, setBusy] = useState(false);

  const [prepTime, setPrepTime] = useState(product.estimatedPreparationMinutes?.toString() ?? "");
  const [allergens, setAllergens] = useState<Set<Allergen>>(new Set(product.allergens));

  const [assignTarget, setAssignTarget] = useState<BranchAssignmentTarget>("ALL_BRANCHES");
  const [assignBranchIds, setAssignBranchIds] = useState<Set<string>>(new Set());

  const isAvailable = branchProduct?.availability === "AVAILABLE";

  function openEdit() {
    setPrepTime(product.estimatedPreparationMinutes?.toString() ?? "");
    setAllergens(new Set(product.allergens));
    setPanel(panel === "edit" ? "none" : "edit");
  }

  function openAssign() {
    setAssignTarget("ALL_BRANCHES");
    setAssignBranchIds(new Set());
    setPanel(panel === "assign" ? "none" : "assign");
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

  function toggleAssignBranch(branchId: string) {
    setAssignBranchIds((current) => {
      const next = new Set(current);
      if (next.has(branchId)) {
        next.delete(branchId);
      } else {
        next.add(branchId);
      }
      return next;
    });
  }

  async function handleToggleAvailability() {
    if (!selectedBranchId) {
      return;
    }
    setBusy(true);
    try {
      const nextAvailability = isAvailable ? "UNAVAILABLE" : "AVAILABLE";
      const updated = await upsertBranchProduct(selectedBranchId, product.id, nextAvailability);
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
      const updated = await updateProductDetails(product.id, !product.active, product.estimatedPreparationMinutes, product.allergens);
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
      const updated = await updateProductDetails(product.id, product.active, prepTimeValue, Array.from(allergens));
      onProductUpdated(updated);
      setPanel("none");
      showToast("Ürün detayları güncellendi.", "success");
    } catch {
      showToast("Ürün detayları güncellenemedi.", "error");
    } finally {
      setBusy(false);
    }
  }

  async function handleBulkAssign() {
    const branchIds = Array.from(assignBranchIds);
    if (assignTarget === "SELECTED_BRANCHES" && branchIds.length === 0) {
      showToast("Seçili şubeler için en az bir şube seçin.", "error");
      return;
    }
    setBusy(true);
    try {
      const updated = await bulkAssignProductToBranches(product.id, assignTarget, branchIds);
      if (selectedBranchId) {
        const forSelectedBranch = updated.find((bp) => bp.branchId === selectedBranchId);
        if (forSelectedBranch) {
          onBranchProductUpdated(forSelectedBranch);
        }
      }
      setPanel("none");
      showToast("Şubelere atandı.", "success");
    } catch {
      showToast("Şubelere atama yapılamadı.", "error");
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
          {selectedBranchId ? <Badge tone={isAvailable ? "neutral" : "danger"}>{isAvailable ? "Şubede satışta" : "Şubede yok"}</Badge> : null}
        </td>
        <td>
          <div className={tableStyles.actions}>
            {selectedBranchId ? (
              <Button size="md" variant="secondary" disabled={busy} onClick={handleToggleAvailability}>
                {isAvailable ? "Kaldır" : "Şubeye Ekle"}
              </Button>
            ) : null}
            <Button size="md" variant="secondary" disabled={busy} onClick={handleTogglePassive}>
              {product.active ? "Pasif Yap" : "Aktif Yap"}
            </Button>
            <Button size="md" variant="ghost" disabled={busy} onClick={openEdit}>
              {panel === "edit" ? "Vazgeç" : "Düzenle"}
            </Button>
            <Button size="md" variant="ghost" disabled={busy} onClick={openAssign}>
              {panel === "assign" ? "Vazgeç" : "Şubelere Ata"}
            </Button>
          </div>
        </td>
      </tr>

      {panel === "edit" ? (
        <tr className={tableStyles.expandedRow}>
          <td colSpan={5}>
            <div className={styles.section}>
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

      {panel === "assign" ? (
        <tr className={tableStyles.expandedRow}>
          <td colSpan={5}>
            <div className={styles.section}>
              <div className={styles.field}>
                <span className={styles.label}>Hedef</span>
                <div className={styles.rowActions}>
                  <label className={styles.rowMeta}>
                    <input
                      type="radio"
                      name={`assign-target-${product.id}`}
                      checked={assignTarget === "ALL_BRANCHES"}
                      onChange={() => setAssignTarget("ALL_BRANCHES")}
                    />{" "}
                    Tüm şubelere ata
                  </label>
                  <label className={styles.rowMeta}>
                    <input
                      type="radio"
                      name={`assign-target-${product.id}`}
                      checked={assignTarget === "SELECTED_BRANCHES"}
                      onChange={() => setAssignTarget("SELECTED_BRANCHES")}
                    />{" "}
                    Seçili şubelere ata
                  </label>
                </div>
              </div>
              {assignTarget === "SELECTED_BRANCHES" ? (
                <div className={styles.field}>
                  <span className={styles.label}>Şubeler</span>
                  <div className={styles.rowActions}>
                    {branches.map((branch) => (
                      <label key={branch.id} className={styles.rowMeta}>
                        <input type="checkbox" checked={assignBranchIds.has(branch.id)} onChange={() => toggleAssignBranch(branch.id)} /> {branch.name}
                      </label>
                    ))}
                  </div>
                </div>
              ) : null}
              <Button size="md" disabled={busy} onClick={handleBulkAssign}>
                Ata
              </Button>
            </div>
          </td>
        </tr>
      ) : null}
    </>
  );
}
