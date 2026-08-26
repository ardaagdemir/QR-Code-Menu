"use client";

import { useEffect, useId, useState } from "react";
import { Plus } from "lucide-react";
import {
  createProduct,
  deleteProduct,
  listBranchProducts,
  listProductsForCategory,
  reorderProducts,
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
import ConfirmDialog from "@/components/ui/ConfirmDialog";
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
  const [reorderingId, setReorderingId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ProductAdmin | null>(null);
  const [deleting, setDeleting] = useState(false);

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
    const preparationMinutes = formValues.preparationMinutes.trim() === "" ? null : Number(formValues.preparationMinutes);
    if (formValues.price.trim() === "" || !Number.isFinite(priceMinorUnits) || priceMinorUnits < 0) {
      setFormError("Geçerli bir fiyat girin.");
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
        imageUrl: formValues.imageUrl,
        estimatedPreparationMinutes: preparationMinutes,
        allergens: formValues.allergens,
      });
      setFormValues(emptyProductFormValues());
      setProducts((current) => [...current, product]);
      setCreateOpen(false);
      showToast("Ürün oluşturuldu.", "success");
      // Backend auto-provisions a BranchProduct row (AVAILABLE) for the active branch on
      // create, but createProduct's response only carries the Product - refetch so this
      // row shows up immediately instead of only after a page refresh.
      listBranchProducts()
        .then(setBranchProducts)
        .catch(() => showToast("Şube ürün durumları yenilenemedi.", "error"));
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

  async function handleMoveProduct(index: number, direction: -1 | 1) {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= products.length) {
      return;
    }
    const reordered = [...products];
    [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
    setReorderingId(products[index].id);
    try {
      const result = await reorderProducts(categoryId, reordered.map((p) => p.id));
      setProducts(result);
    } catch {
      showToast("Ürün sırası güncellenemedi.", "error");
    } finally {
      setReorderingId(null);
    }
  }

  async function handleConfirmDeleteProduct() {
    if (!deleteTarget) {
      return;
    }
    setDeleting(true);
    try {
      await deleteProduct(deleteTarget.id);
      setProducts((current) => current.filter((p) => p.id !== deleteTarget.id));
      showToast("Ürün silindi.", "success");
    } catch {
      showToast("Ürün silinemedi.", "error");
    } finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
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
            {products.map((product, index) => (
              <ProductRow
                key={product.id}
                product={product}
                branchProduct={branchProducts.find((bp) => bp.productId === product.id)}
                onProductUpdated={handleProductUpdated}
                onBranchProductUpdated={handleBranchProductUpdated}
                canMoveUp={index > 0}
                canMoveDown={index < products.length - 1}
                reorderDisabled={reorderingId !== null}
                onMoveUp={() => handleMoveProduct(index, -1)}
                onMoveDown={() => handleMoveProduct(index, 1)}
                onRequestDelete={() => setDeleteTarget(product)}
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
            <ProductFormFields values={formValues} onChange={setFormValues} />
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

      {deleteTarget ? (
        <ConfirmDialog
          title="Ürünü Sil"
          message={`"${deleteTarget.name}" ürünü, tüm option'ları/seçenek gruplarını ve şube atamalarıyla birlikte kalıcı olarak silinecek. Bu işlem geri alınamaz.`}
          confirmLabel="Sil"
          tone="danger"
          confirmLoading={deleting}
          onConfirm={handleConfirmDeleteProduct}
          onCancel={() => setDeleteTarget(null)}
        />
      ) : null}
    </section>
  );
}
