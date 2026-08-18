"use client";

import { useEffect, useId, useState } from "react";
import { Plus } from "lucide-react";
import {
  createProduct,
  listBranchProducts,
  listProductsForCategory,
  uploadProductImage,
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
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import FileUploadField from "@/components/ui/FileUploadField";
import { useToast } from "@/components/ui/ToastProvider";
import ProductRow from "./ProductRow";
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
  const [productName, setProductName] = useState("");
  const [productPrice, setProductPrice] = useState("");
  const [productTax, setProductTax] = useState("10");
  const [productImageUrl, setProductImageUrl] = useState<string | null>(null);
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
    if (!productName.trim()) {
      return;
    }
    const priceMinorUnits = Math.round(Number(productPrice.replace(",", ".")) * 100);
    const taxRatePercent = Number(productTax);
    if (!Number.isFinite(priceMinorUnits) || priceMinorUnits < 0 || !Number.isInteger(taxRatePercent)) {
      setFormError("Geçerli bir fiyat ve KDV oranı girin.");
      return;
    }
    setCreating(true);
    setFormError(null);
    try {
      const product = await createProduct(categoryId, productName.trim(), priceMinorUnits, taxRatePercent, productImageUrl);
      setProductName("");
      setProductPrice("");
      setProductImageUrl(null);
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
          <Button onClick={() => setCreateOpen(true)}>
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
        <Dialog onClose={() => setCreateOpen(false)} labelledBy={dialogTitleId}>
          <h2 id={dialogTitleId} className={styles.sectionTitle}>
            Yeni Ürün
          </h2>
          <form className={styles.section} onSubmit={handleCreateProduct}>
            <FormField label="Ürün adı" required>
              {(controlProps) => <Input {...controlProps} value={productName} onChange={(event) => setProductName(event.target.value)} required />}
            </FormField>
            <FormField label="Fiyat (₺)" required>
              {(controlProps) => (
                <Input {...controlProps} inputMode="decimal" value={productPrice} onChange={(event) => setProductPrice(event.target.value)} required />
              )}
            </FormField>
            <FormField label="KDV %" required>
              {(controlProps) => (
                <Input {...controlProps} inputMode="numeric" value={productTax} onChange={(event) => setProductTax(event.target.value)} required />
              )}
            </FormField>
            <FileUploadField
              label="Ürün görseli"
              hint="JPEG, PNG veya WEBP - en fazla 5MB."
              accept="image/jpeg,image/png,image/webp"
              value={productImageUrl}
              onChange={setProductImageUrl}
              upload={uploadProductImage}
            />
            {formError ? <ErrorState message={formError} /> : null}
            <Button type="submit" disabled={creating}>
              {creating ? "Oluşturuluyor…" : "Ürün Ekle"}
            </Button>
          </form>
        </Dialog>
      ) : null}
    </section>
  );
}
