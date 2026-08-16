"use client";

import { useEffect, useState } from "react";
import { listMenuCategories, type MenuCategoryAdmin } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import ErrorState from "@/components/ui/ErrorState";
import CategoriesSection from "./features/CategoriesSection";
import ProductsSection from "./features/ProductsSection";
import styles from "@/styles/admin.module.css";

/**
 * Section 4, staff-web admin screen: menu management (Permission.MENU_MANAGE) plus
 * per-branch opt-in (Section 5). Bölüm 19.3 "büyük menu ... sayfaları feature/
 * component parçalarına ayrılır": bu sayfa yalnızca kategori/şube seçim state'ini
 * tutan bir orkestratör, kategori ve ürün CRUD'u kendi feature component'lerinde.
 */
export default function MenuPage() {
  const [categories, setCategories] = useState<MenuCategoryAdmin[]>([]);
  const [selectedCategoryId, setSelectedCategoryId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listMenuCategories()
      .then((categoryList) => {
        setCategories(categoryList);
        if (categoryList.length > 0) {
          setSelectedCategoryId(categoryList[0].id);
        }
      })
      .catch(() => setError("Menü verileri yüklenemedi."));
  }, []);

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader title="Menü Yönetimi" />

        {error ? <ErrorState message={error} /> : null}

        <CategoriesSection
          categories={categories}
          selectedCategoryId={selectedCategoryId}
          onSelectCategory={setSelectedCategoryId}
          onCategoryCreated={(category) => {
            setCategories((current) => [...current, category]);
            setSelectedCategoryId(category.id);
          }}
        />

        {selectedCategoryId ? (
          <ProductsSection categoryId={selectedCategoryId} />
        ) : null}
      </main>
    </AppShell>
  );
}
