"use client";

import { useEffect, useRef, useState } from "react";
import {
  ALLERGENS,
  createMenuCategory,
  createProduct,
  listBranches,
  listBranchProducts,
  listMenuCategories,
  listProductsForCategory,
  updateProductDetails,
  upsertBranchProduct,
  type Allergen,
  type Branch,
  type BranchProductAdmin,
  type MenuCategoryAdmin,
  type ProductAdmin,
} from "@/lib/api";
import StaffNav from "@/components/layout/StaffNav";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
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
  const [editingProductId, setEditingProductId] = useState<string | null>(null);
  const prepTimeRef = useRef<Record<string, string>>({});
  const allergensRef = useRef<Record<string, Set<Allergen>>>({});

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

  function startEditingProduct(product: ProductAdmin) {
    prepTimeRef.current[product.id] = product.estimatedPreparationMinutes?.toString() ?? "";
    allergensRef.current[product.id] = new Set(product.allergens);
    setEditingProductId(product.id);
  }

  async function handleTogglePassive(product: ProductAdmin) {
    setBusy(true);
    setError(null);
    try {
      const updated = await updateProductDetails(
        product.id,
        !product.active,
        product.estimatedPreparationMinutes,
        product.allergens,
      );
      setProducts((current) => current.map((p) => (p.id === updated.id ? updated : p)));
    } catch {
      setError("Ürün durumu güncellenemedi.");
    } finally {
      setBusy(false);
    }
  }

  async function handleSaveProductDetails(product: ProductAdmin) {
    const prepTimeInput = prepTimeRef.current[product.id] ?? "";
    const prepTime = prepTimeInput.trim() === "" ? null : Number(prepTimeInput);
    if (prepTime !== null && (!Number.isInteger(prepTime) || prepTime < 0)) {
      setError("Geçerli bir hazırlık süresi girin.");
      return;
    }
    const allergens = Array.from(allergensRef.current[product.id] ?? new Set<Allergen>());
    setBusy(true);
    setError(null);
    try {
      const updated = await updateProductDetails(product.id, product.active, prepTime, allergens);
      setProducts((current) => current.map((p) => (p.id === updated.id ? updated : p)));
      setEditingProductId(null);
    } catch {
      setError("Ürün detayları güncellenemedi.");
    } finally {
      setBusy(false);
    }
  }

  function toggleAllergen(productId: string, allergen: Allergen) {
    const current = allergensRef.current[productId] ?? new Set<Allergen>();
    if (current.has(allergen)) {
      current.delete(allergen);
    } else {
      current.add(allergen);
    }
    allergensRef.current[productId] = new Set(current);
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
                  const isEditing = editingProductId === product.id;
                  return (
                    <div key={product.id}>
                      <div className={styles.row}>
                        <div className={styles.rowMain}>
                          <span className={styles.rowTitle}>{product.name}</span>
                          <span className={styles.rowMeta}>
                            {(product.basePriceMinorUnits / 100).toFixed(2)} ₺ · KDV %{product.taxRatePercent}
                            {product.estimatedPreparationMinutes != null
                              ? ` · ~${product.estimatedPreparationMinutes} dk`
                              : ""}
                            {product.allergens.length > 0
                              ? ` · ${product.allergens.map((a) => ALLERGEN_LABELS[a]).join(", ")}`
                              : ""}
                          </span>
                        </div>
                        <div className={styles.rowActions}>
                          {!product.active ? <Badge tone="danger">Pasif</Badge> : null}
                          <Badge tone={isAvailable ? "neutral" : "danger"}>{isAvailable ? "Şubede satışta" : "Şubede yok"}</Badge>
                          {selectedBranchId ? (
                            <Button size="md" variant="secondary" disabled={busy} onClick={() => handleToggleAvailability(product)}>
                              {isAvailable ? "Kaldır" : "Şubeye Ekle"}
                            </Button>
                          ) : null}
                          <Button size="md" variant="secondary" disabled={busy} onClick={() => handleTogglePassive(product)}>
                            {product.active ? "Pasif Yap" : "Aktif Yap"}
                          </Button>
                          <Button
                            size="md"
                            variant="ghost"
                            disabled={busy}
                            onClick={() => (isEditing ? setEditingProductId(null) : startEditingProduct(product))}
                          >
                            {isEditing ? "Vazgeç" : "Düzenle"}
                          </Button>
                        </div>
                      </div>

                      {isEditing ? (
                        <div className={styles.row}>
                          <div className={styles.field}>
                            <label className={styles.label} htmlFor={`prep-time-${product.id}`}>
                              Hazırlık süresi (dk)
                            </label>
                            <input
                              id={`prep-time-${product.id}`}
                              className={styles.input}
                              inputMode="numeric"
                              defaultValue={product.estimatedPreparationMinutes ?? ""}
                              onChange={(event) => {
                                prepTimeRef.current[product.id] = event.target.value;
                              }}
                            />
                          </div>
                          <div className={styles.field}>
                            <span className={styles.label}>Alerjenler</span>
                            <div className={styles.rowActions}>
                              {ALLERGENS.map((allergen) => (
                                <label key={allergen} className={styles.rowMeta}>
                                  <input
                                    type="checkbox"
                                    defaultChecked={product.allergens.includes(allergen)}
                                    onChange={() => toggleAllergen(product.id, allergen)}
                                  />{" "}
                                  {ALLERGEN_LABELS[allergen]}
                                </label>
                              ))}
                            </div>
                          </div>
                          <Button size="md" disabled={busy} onClick={() => handleSaveProductDetails(product)}>
                            Kaydet
                          </Button>
                        </div>
                      ) : null}
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
