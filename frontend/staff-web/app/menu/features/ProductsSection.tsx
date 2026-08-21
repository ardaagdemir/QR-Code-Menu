"use client";

import { useEffect, useId, useState } from "react";
import { Plus } from "lucide-react";
import {
  createProduct,
  listBranchProducts,
  listProductsForCategory,
  type BranchProductAdmin,
  type ProductAdmin,
} from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import { useToast } from "@/components/ui/ToastProvider";
import ProductRow from "./ProductRow";
import ProductFormFields, { emptyProductFormValues, type ProductFormValues } from "./ProductFormFields";
import styles from "@/styles/admin.module.css";
import menuStyles from "../menu.module.css";

type Props = {
  categoryId: string;
};

/** Menü Yönetimi'nin ürün paneli - Milestone 4'ün "opt-in" kuralı: bir ürün, ilgili
 * şubede bir BranchProduct satırı oluşana kadar o şubede görünmez. */
export default function ProductsSection({ categoryId }: Props) {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [products, setProducts] = useState<ProductAdmin[]>([]);
  const [branchProducts, setBranchProducts] = useState<BranchProductAdmin[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [formValues, setFormValues] = useState<ProductFormValues>(emptyProductFormValues);
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    listProductsForCategory(categoryId)
      .then((data) => {
        setProducts(data);
        setError(null);
      })
      .catch(() => setError("Ürünler yüklenemedi."))
      .finally(() => setLoading(false));
  }, [categoryId]);

  useEffect(() => {
    listBranchProducts()
      .then(setBranchProducts)
      .catch(() => showToast("Şube ürün durumları yüklenemedi.", "error"));
  }, [showToast]);

  async function handleCreateProduct(event: React.FormEvent) {
    event.preventDefault();
    if (!formValues.name.trim()) {
      return;
    }
    const priceMinorUnits = Math.round(Number(formValues.price.replace(",", ".")) * 100);
    const taxRatePercent = Number(formValues.taxRate);
    const preparationMinutes = formValues.preparationMinutes.trim() === "" ? null : Number(formValues.preparationMinutes);
    if (
      formValues.price.trim() === "" ||
      !Number.isFinite(priceMinorUnits) ||
      priceMinorUnits < 0 ||
      !Number.isInteger(taxRatePercent) ||
      taxRatePercent < 0
    ) {
      setFormError("Geçerli bir fiyat ve KDV oranı girin.");
      return;
    }
    if (preparationMinutes !== null && (!Number.isInteger(preparationMinutes) || preparationMinutes < 0)) {
      setFormError("Hazırlık süresi sıfır veya pozitif bir tam sayı olmalı.");
      return;
    }
    setCreating(true);
    setFormError(null);
    try {
      const product = await createProduct({
        categoryId,
        name: formValues.name.trim(),
        description: formValues.description.trim() || null,
        basePriceMinorUnits: priceMinorUnits,
        taxRatePercent,
        imageUrl: formValues.imageUrl,
        estimatedPreparationMinutes: preparationMinutes,
        allergens: formValues.allergens,
      });
      setFormValues(emptyProductFormValues());
      setProducts((current) => [...current, product]);
      setCreateOpen(false);
      showToast("Ürün oluşturuldu.", "success");
    } catch {
      setFormError("Ürün oluşturulamadı.");
    } finally {
      setCreating(false);
    }
  }

  function handleProductUpdated(updated: ProductAdmin) {
    setProducts((current) => current.map((p) => (p.id === updated.id ? updated : p)));
  }

  function handleBranchProductUpdated(updated: BranchProductAdmin) {
    setBranchProducts((current) => [...current.filter((bp) => bp.productId !== updated.productId), updated]);
  }

  return (
    <section className={`${styles.section} ${styles.panel} ${menuStyles.productPanel}`}>
      <PageHeader
        title="Ürünler"
        description="Seçili kategorideki ürün detaylarını ve şube uygunluğunu yönetin."
        actions={
          <Button
            onClick={() => {
              setFormValues(emptyProductFormValues());
              setFormError(null);
              setCreateOpen(true);
            }}
          >
            <Plus size={16} aria-hidden="true" /> Ürün Ekle
          </Button>
        }
      />

      {loading ? (
        <TableSkeleton />
      ) : error ? (
        <ErrorState message={error} />
      ) : products.length === 0 ? (
        <EmptyState title="Bu kategoride ürün yok" />
      ) : (
        <Table>
          <thead>
            <tr>
              <th>Ürün</th>
              <th>Fiyat</th>
              <th>Alerjenler</th>
              <th>Durum</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {products.map((product) => (
              <ProductRow
                key={product.id}
                product={product}
                branchProduct={branchProducts.find((bp) => bp.productId === product.id)}
                onProductUpdated={handleProductUpdated}
                onBranchProductUpdated={handleBranchProductUpdated}
              />
            ))}
          </tbody>
        </Table>
      )}

      {createOpen ? (
        <Dialog onClose={() => setCreateOpen(false)} labelledBy={dialogTitleId} size="lg">
          <h2 id={dialogTitleId} className={styles.sectionTitle}>
            Yeni Ürün
          </h2>
          <form className={`${styles.section} ${menuStyles.productDialogForm}`} onSubmit={handleCreateProduct}>
            <ProductFormFields values={formValues} onChange={setFormValues} mode="create" />
            {formError ? <ErrorState message={formError} /> : null}
            <div className={menuStyles.productFormActions}>
              <Button type="button" variant="secondary" disabled={creating} onClick={() => setCreateOpen(false)}>
                Vazgeç
              </Button>
              <Button type="submit" disabled={creating}>
                {creating ? "Oluşturuluyor…" : "Ürün Ekle"}
              </Button>
            </div>
          </form>
        </Dialog>
      ) : null}
    </section>
  );
}
