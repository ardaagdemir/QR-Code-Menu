"use client";

import { useEffect, useState } from "react";
import {
  createMenuCategory,
  createProduct,
  listBranches,
  listBranchProducts,
  listMenuCategories,
  listProductsForCategory,
  upsertBranchProduct,
  type Branch,
  type BranchProductAdmin,
  type MenuCategoryAdmin,
  type ProductAdmin,
} from "@/lib/api";
import StaffNav from "@/components/layout/StaffNav";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import styles from "@/styles/admin.module.css";

/**
 * Section 4, staff-web admin screen: menu management (Permission.MENU_MANAGE) plus
 * per-branch opt-in (Section 5: a product is invisible at a branch until a
 * BranchProduct row exists for it - Milestone 4's "opt-in" rule).
 */
export default function MenuPage() {
  const [categories, setCategories] = useState<MenuCategoryAdmin[]>([]);
  const [selectedCategoryId, setSelectedCategoryId] = useState<string | null>(null);
  const [products, setProducts] = useState<ProductAdmin[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [selectedBranchId, setSelectedBranchId] = useState<string | null>(null);
  const [branchProducts, setBranchProducts] = useState<BranchProductAdmin[]>([]);

  const [categoryName, setCategoryName] = useState("");
  const [productName, setProductName] = useState("");
  const [productPrice, setProductPrice] = useState("");
  const [productTax, setProductTax] = useState("10");

  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    Promise.all([listMenuCategories(), listBranches()])
      .then(([categoryList, branchList]) => {
        setCategories(categoryList);
        setBranches(branchList);
        if (categoryList.length > 0) {
          setSelectedCategoryId(categoryList[0].id);
        }
        if (branchList.length > 0) {
          setSelectedBranchId(branchList[0].id);
        }
      })
      .catch(() => setError("Menü verileri yüklenemedi."));
  }, []);

  useEffect(() => {
    async function fetchProducts() {
      if (!selectedCategoryId) {
        setProducts([]);
        return;
      }
      try {
        setProducts(await listProductsForCategory(selectedCategoryId));
      } catch {
        setError("Ürünler yüklenemedi.");
      }
    }
    void fetchProducts();
  }, [selectedCategoryId]);

  useEffect(() => {
    async function fetchBranchProducts() {
      if (!selectedBranchId) {
        setBranchProducts([]);
        return;
      }
      try {
        setBranchProducts(await listBranchProducts(selectedBranchId));
      } catch {
        setError("Şube ürün durumları yüklenemedi.");
      }
    }
    void fetchBranchProducts();
  }, [selectedBranchId]);

  async function handleCreateCategory(event: React.FormEvent) {
    event.preventDefault();
    if (!categoryName.trim()) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const category = await createMenuCategory(categoryName.trim());
      setCategoryName("");
      setCategories((current) => [...current, category]);
      setSelectedCategoryId(category.id);
    } catch {
      setError("Kategori oluşturulamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function handleCreateProduct(event: React.FormEvent) {
    event.preventDefault();
    if (!selectedCategoryId || !productName.trim()) {
      return;
    }
    const priceMinorUnits = Math.round(Number(productPrice.replace(",", ".")) * 100);
    const taxRatePercent = Number(productTax);
    if (!Number.isFinite(priceMinorUnits) || priceMinorUnits < 0 || !Number.isInteger(taxRatePercent)) {
      setError("Geçerli bir fiyat ve KDV oranı girin.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const product = await createProduct(selectedCategoryId, productName.trim(), priceMinorUnits, taxRatePercent);
      setProductName("");
      setProductPrice("");
      setProducts((current) => [...current, product]);
    } catch {
      setError("Ürün oluşturulamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function handleToggleAvailability(product: ProductAdmin) {
    if (!selectedBranchId) {
      return;
    }
    const existing = branchProducts.find((bp) => bp.productId === product.id);
    const nextAvailability = existing?.availability === "AVAILABLE" ? "UNAVAILABLE" : "AVAILABLE";
    setBusy(true);
    setError(null);
    try {
      const updated = await upsertBranchProduct(selectedBranchId, product.id, nextAvailability);
      setBranchProducts((current) => {
        const rest = current.filter((bp) => bp.productId !== product.id);
        return [...rest, updated];
      });
    } catch {
      setError("Şube ürün durumu güncellenemedi.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <StaffNav />
      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>Menü Yönetimi</h1>
        </div>

        {error ? <p className={styles.error}>{error}</p> : null}

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Kategoriler</h2>
          <form className={styles.form} onSubmit={handleCreateCategory}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="category-name">
                Yeni kategori adı
              </label>
              <input
                id="category-name"
                className={styles.input}
                value={categoryName}
                onChange={(event) => setCategoryName(event.target.value)}
                required
              />
            </div>
            <Button type="submit" disabled={busy}>
              Kategori Ekle
            </Button>
          </form>
          <div className={styles.list}>
            {categories.length === 0 ? (
              <p className={styles.empty}>Henüz kategori yok.</p>
            ) : (
              categories.map((category) => (
                <div key={category.id} className={styles.row}>
                  <button
                    type="button"
                    className={`${styles.rowTitle} ${styles.linkButton}`}
                    onClick={() => setSelectedCategoryId(category.id)}
                  >
                    {category.name}
                    {selectedCategoryId === category.id ? " (seçili)" : ""}
                  </button>
                </div>
              ))
            )}
          </div>
        </section>

        {selectedCategoryId ? (
          <section className={styles.section}>
            <h2 className={styles.sectionTitle}>Ürünler</h2>
            <form className={styles.form} onSubmit={handleCreateProduct}>
              <div className={styles.field}>
                <label className={styles.label} htmlFor="product-name">
                  Ürün adı
                </label>
                <input
                  id="product-name"
                  className={styles.input}
                  value={productName}
                  onChange={(event) => setProductName(event.target.value)}
                  required
                />
              </div>
              <div className={styles.field}>
                <label className={styles.label} htmlFor="product-price">
                  Fiyat (₺)
                </label>
                <input
                  id="product-price"
                  className={styles.input}
                  inputMode="decimal"
                  value={productPrice}
                  onChange={(event) => setProductPrice(event.target.value)}
                  required
                />
              </div>
              <div className={styles.field}>
                <label className={styles.label} htmlFor="product-tax">
                  KDV %
                </label>
                <input
                  id="product-tax"
                  className={styles.input}
                  inputMode="numeric"
                  value={productTax}
                  onChange={(event) => setProductTax(event.target.value)}
                  required
                />
              </div>
              <Button type="submit" disabled={busy}>
                Ürün Ekle
              </Button>
            </form>

            {branches.length > 0 ? (
              <div className={styles.field}>
                <label className={styles.label} htmlFor="branch-select">
                  Şube (satış durumu için)
                </label>
                <select
                  id="branch-select"
                  className={styles.select}
                  value={selectedBranchId ?? ""}
                  onChange={(event) => setSelectedBranchId(event.target.value)}
                >
                  {branches.map((branch) => (
                    <option key={branch.id} value={branch.id}>
                      {branch.name}
                    </option>
                  ))}
                </select>
              </div>
            ) : null}

            <div className={styles.list}>
              {products.length === 0 ? (
                <p className={styles.empty}>Bu kategoride ürün yok.</p>
              ) : (
                products.map((product) => {
                  const branchProduct = branchProducts.find((bp) => bp.productId === product.id);
                  const isAvailable = branchProduct?.availability === "AVAILABLE";
                  return (
                    <div key={product.id} className={styles.row}>
                      <div className={styles.rowMain}>
                        <span className={styles.rowTitle}>{product.name}</span>
                        <span className={styles.rowMeta}>
                          {(product.basePriceMinorUnits / 100).toFixed(2)} ₺ · KDV %{product.taxRatePercent}
                        </span>
                      </div>
                      <div className={styles.rowActions}>
                        <Badge tone={isAvailable ? "neutral" : "danger"}>{isAvailable ? "Şubede satışta" : "Şubede yok"}</Badge>
                        {selectedBranchId ? (
                          <Button size="md" variant="secondary" disabled={busy} onClick={() => handleToggleAvailability(product)}>
                            {isAvailable ? "Kaldır" : "Şubeye Ekle"}
                          </Button>
                        ) : null}
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </section>
        ) : null}
      </main>
    </>
  );
}
