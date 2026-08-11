"use client";

import { useState } from "react";
import { ALLERGEN_LABELS, formatPriceMinorUnits, type MenuProduct } from "@/lib/api";
import Badge from "@/components/ui/Badge";
import styles from "./ProductCard.module.css";

type Props = {
  product: MenuProduct;
  onSelect: (product: MenuProduct) => void;
};

export default function ProductCard({ product, onSelect }: Props) {
  const isAvailable = product.availability === "AVAILABLE";
  const [imageFailed, setImageFailed] = useState(false);
  const showImage = Boolean(product.imageUrl) && !imageFailed;

  return (
    <li className={styles.item}>
      <button
        type="button"
        className={styles.card}
        disabled={!isAvailable}
        onClick={() => onSelect(product)}
        aria-label={isAvailable ? product.name : `${product.name} - tükendi`}
      >
        <div className={styles.media}>
          {showImage ? (
            // Plain <img>, not next/image: Product.imageUrl is an arbitrary external URL
            // with no media-storage/CDN pipeline behind it (Bölüm 5, 14) - aspect-ratio on
            // .media already prevents layout shift without next/image's config overhead.
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={product.imageUrl ?? undefined}
              alt=""
              loading="lazy"
              className={styles.image}
              onError={() => setImageFailed(true)}
            />
          ) : (
            <div className={styles.imagePlaceholder} aria-hidden="true">
              🍽️
            </div>
          )}
          {!isAvailable ? (
            <div className={styles.soldOutOverlay}>
              <Badge tone="danger">Tükendi</Badge>
            </div>
          ) : null}
        </div>
        <div className={styles.body}>
          <div className={styles.headerRow}>
            <span className={styles.name}>{product.name}</span>
            <span className={styles.price}>{formatPriceMinorUnits(product.priceMinorUnits)}</span>
          </div>
          {product.description ? <p className={styles.description}>{product.description}</p> : null}
          {product.estimatedPreparationMinutes != null || product.allergens.length > 0 ? (
            <p className={styles.meta}>
              {product.estimatedPreparationMinutes != null ? `~${product.estimatedPreparationMinutes} dk` : ""}
              {product.estimatedPreparationMinutes != null && product.allergens.length > 0 ? " · " : ""}
              {product.allergens.length > 0
                ? `İçerir: ${product.allergens.map((allergen) => ALLERGEN_LABELS[allergen] ?? allergen).join(", ")}`
                : ""}
            </p>
          ) : null}
        </div>
      </button>
    </li>
  );
}
