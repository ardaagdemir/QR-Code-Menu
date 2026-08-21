"use client";

import { useState } from "react";
import type { MenuProduct } from "@/lib/api";
import BottomSheet from "@/components/ui/BottomSheet";
import ProductCard from "./ProductCard";
import styles from "./ProductRowSection.module.css";

type Props = {
  title: string;
  products: MenuProduct[];
  onSelectProduct: (product: MenuProduct) => void;
  variant: "featured" | "compact";
  favoriteIds: Set<string>;
  onToggleFavorite: (productId: string) => void;
  badge?: string;
  /** Yalnızca "En Çok Tercih Edilenler" için: satır yatay kaydırmalı olduğundan
   * (hepsi zaten DOM'da) bu buton yeni veri açmaz, hepsini tek bakışta dikey bir
   * grid olarak gösteren bir sheet açar - kaydırmanın "bunlar hepsi mi?" belirsizliğini
   * gidermek için. */
  showSeeAll?: boolean;
};

/** "En Çok Tercih Edilenler" ve "Favoriler" için ortak yatay kaydırmalı satır - her
 * ikisi de veri yoksa (favori yok / satış geçmişi yok) hiç render edilmez. */
export default function ProductRowSection({
  title,
  products,
  onSelectProduct,
  variant,
  favoriteIds,
  onToggleFavorite,
  badge,
  showSeeAll = false,
}: Props) {
  const [seeAllOpen, setSeeAllOpen] = useState(false);

  if (products.length === 0) {
    return null;
  }

  return (
    <section className={styles.section} aria-label={title}>
      <div className={styles.headingRow}>
        <h2 className={styles.heading}>{title}</h2>
        {showSeeAll && products.length > 3 ? (
          <button type="button" className={styles.seeAllButton} onClick={() => setSeeAllOpen(true)}>
            Tümünü Gör
          </button>
        ) : null}
      </div>
      <ul className={styles.row}>
        {products.map((product) => (
          <ProductCard
            key={product.id}
            product={product}
            onSelect={onSelectProduct}
            variant={variant}
            isFavorite={favoriteIds.has(product.id)}
            onToggleFavorite={onToggleFavorite}
            badge={badge}
          />
        ))}
      </ul>

      {seeAllOpen ? (
        <BottomSheet onClose={() => setSeeAllOpen(false)} labelledBy="see-all-title">
          <h2 id="see-all-title" className={styles.sheetTitle}>
            {title}
          </h2>
          <ul className={styles.sheetGrid}>
            {products.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                onSelect={(selected) => {
                  setSeeAllOpen(false);
                  onSelectProduct(selected);
                }}
                variant="grid"
                isFavorite={favoriteIds.has(product.id)}
                onToggleFavorite={onToggleFavorite}
              />
            ))}
          </ul>
        </BottomSheet>
      ) : null}
    </section>
  );
}
