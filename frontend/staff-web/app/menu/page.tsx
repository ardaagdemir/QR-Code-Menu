"use client";

import { useCallback, useEffect, useState } from "react";
import { listMenuCategories, type MenuCategoryAdmin } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import CategoriesSection from "./features/CategoriesSection";
import ProductsSection from "./features/ProductsSection";
import styles from "@/styles/admin.module.css";
import menuStyles from "./menu.module.css";

/**
 * Section 4, staff-web admin screen: menu management (Permission.MENU_MANAGE) plus
 * per-branch opt-in (Section 5). Bölüm 19.3 "büyük menu ... sayfaları feature/
 * component parçalarına ayrılır": bu sayfa yalnızca kategori/şube seçim state'ini
 * tutan bir orkestratör, kategori ve ürün CRUD'u kendi feature component'lerinde.
 */
export default function MenuPage() {
  const [categories, setCategories] = useState<MenuCategoryAdmin[]>([]);
  const [selectedCategoryId, setSelectedCategoryId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadCategories = useCallback(() => {
    listMenuCategories()
      .then((categoryList) => {
        setCategories(categoryList);
        setError(null);
        if (categoryList.length > 0) {
          setSelectedCategoryId(categoryList[0].id);
        }
      })
      .catch(() => setError("Menü verileri yüklenemedi."))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    loadCategories();
  }, [loadCategories]);

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader title="Menü" description="Kategorileri, ürünleri ve aktif şubedeki satış durumlarını yönetin." />

        {loading ? (
          <TableSkeleton rows={6} />
        ) : error ? (
          <ErrorState
            message={error}
            onRetry={() => {
              setLoading(true);
              loadCategories();
            }}
          />
        ) : (
          <div className={menuStyles.menuGrid}>
            <CategoriesSection
              categories={categories}
              selectedCategoryId={selectedCategoryId}
              onSelectCategory={setSelectedCategoryId}
              onCategoryCreated={(category) => {
                setCategories((current) => [...current, category]);
                setSelectedCategoryId(category.id);
              }}
              onCategoryRenamed={(category) => {
                setCategories((current) => current.map((c) => (c.id === category.id ? category : c)));
              }}
              onCategoriesReordered={setCategories}
              onCategoryDeleted={(categoryId) => {
                const remaining = categories.filter((c) => c.id !== categoryId);
                setCategories(remaining);
                if (selectedCategoryId === categoryId) {
                  setSelectedCategoryId(remaining[0]?.id ?? null);
                }
              }}
            />

            {selectedCategoryId ? <ProductsSection categoryId={selectedCategoryId} /> : null}
          </div>
        )}
      </main>
    </AppShell>
  );
}
