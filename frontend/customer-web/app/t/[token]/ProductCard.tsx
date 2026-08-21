"use client";

import { useState } from "react";
import { ALLERGEN_LABELS, formatPriceMinorUnits, type MenuProduct } from "@/lib/api";
import Badge from "@/components/ui/Badge";
import DishPlaceholderIcon from "@/components/ui/DishPlaceholderIcon";
import styles from "./ProductCard.module.css";

type Variant = "grid" | "featured" | "compact";

type Props = {
  product: MenuProduct;
  onSelect: (product: MenuProduct) => void;
  variant?: Variant;
  isFavorite?: boolean;
  onToggleFavorite?: (productId: string) => void;
  badge?: string;
};

/** "+ Ekle" ve kartın geneli aynı davranışı tetikler - ikisi de onSelect ile mevcut
 * ProductOptionsSheet akışını açar, sepete ekleme mantığı burada tekrarlanmaz. Kalp
 * butonu, tıklamanın karta yayılmasını (stopPropagation) engeller.
 *
 * "compact" (Favoriler şeridi) referanstaki gibi yatay bir satır - küçük kare thumbnail +
 * isim/fiyat + kalp aynı satırda; "grid"/"featured" ise fotoğraf-ağırlıklı dikey kart. */
export default function ProductCard({
  product,
  onSelect,
  variant = "grid",
  isFavorite = false,
  onToggleFavorite,
  badge,
}: Props) {
  const isAvailable = product.availability === "AVAILABLE";
  const [imageFailed, setImageFailed] = useState(false);
  const showImage = Boolean(product.imageUrl) && !imageFailed;

  const media = showImage ? (
    // Plain <img>, not next/image: Product.imageUrl is an arbitrary external URL
    // with no media-storage/CDN pipeline behind it - aspect-ratio on .media
    // already prevents layout shift without next/image's config overhead.
    // eslint-disable-next-line @next/next/no-img-element
    <img src={product.imageUrl ?? undefined} alt="" loading="lazy" className={styles.image} onError={() => setImageFailed(true)} />
  ) : (
    <div className={styles.imagePlaceholder} aria-hidden="true">
      <DishPlaceholderIcon />
    </div>
  );

  const favoriteButton = onToggleFavorite ? (
    <button
      type="button"
      className={[styles.favoriteButton, isFavorite ? styles.favoriteButtonActive : ""].filter(Boolean).join(" ")}
      onClick={(event) => {
        event.stopPropagation();
        onToggleFavorite(product.id);
      }}
      aria-pressed={isFavorite}
      aria-label={isFavorite ? `${product.name} favorilerden çıkar` : `${product.name} favorilere ekle`}
    >
      <HeartIcon filled={isFavorite} />
    </button>
  ) : null;

  if (variant === "compact") {
    return (
      <li className={styles.item}>
        <div className={[styles.card, styles.compact].filter(Boolean).join(" ")}>
          <button
            type="button"
            className={styles.tapAreaCompact}
            disabled={!isAvailable}
            onClick={() => onSelect(product)}
            aria-label={isAvailable ? product.name : `${product.name} - tükendi`}
          >
            <div className={styles.compactMedia}>{media}</div>
            <div className={styles.compactBody}>
              <span className={styles.name}>{product.name}</span>
              <span className={styles.price}>{formatPriceMinorUnits(product.priceMinorUnits)}</span>
            </div>
          </button>
          {favoriteButton}
        </div>
      </li>
    );
  }

  return (
    <li className={styles.item}>
      <div className={[styles.card, styles[variant]].filter(Boolean).join(" ")}>
        <button
          type="button"
          className={styles.tapArea}
          disabled={!isAvailable}
          onClick={() => onSelect(product)}
          aria-label={isAvailable ? product.name : `${product.name} - tükendi`}
        >
          <div className={styles.media}>
            {media}
            {!isAvailable ? (
              <div className={styles.soldOutOverlay}>
                <Badge tone="danger">Tükendi</Badge>
              </div>
            ) : null}
            {badge ? <span className={styles.featuredBadge}>★ {badge}</span> : null}
          </div>
          <div className={styles.body}>
            <span className={styles.name}>{product.name}</span>
            {product.description ? <p className={styles.description}>{product.description}</p> : null}
            {variant === "grid" && (product.estimatedPreparationMinutes != null || product.allergens.length > 0) ? (
              <p className={styles.meta}>
                {product.estimatedPreparationMinutes != null ? `~${product.estimatedPreparationMinutes} dk` : ""}
                {product.estimatedPreparationMinutes != null && product.allergens.length > 0 ? " · " : ""}
                {product.allergens.length > 0
                  ? `İçerir: ${product.allergens.map((allergen) => ALLERGEN_LABELS[allergen] ?? allergen).join(", ")}`
                  : ""}
              </p>
            ) : null}
            <div className={styles.footerRow}>
              <span className={styles.price}>{formatPriceMinorUnits(product.priceMinorUnits)}</span>
              {variant === "grid" ? <span className={styles.addButton}>+ Ekle</span> : null}
            </div>
          </div>
        </button>

        {favoriteButton}
      </div>
    </li>
  );
}

function HeartIcon({ filled }: { filled: boolean }) {
  return (
    <svg width={16} height={16} viewBox="0 0 24 24" fill={filled ? "currentColor" : "none"} stroke="currentColor" strokeWidth={1.75} aria-hidden="true">
      <path
        d="M12 20.5s-7.5-4.6-10-9.1C0.3 7.9 2 4.5 5.4 4.5c2 0 3.5 1.1 4.6 2.7 1.1-1.6 2.6-2.7 4.6-2.7 3.4 0 5.1 3.4 3.4 6.9-2.5 4.5-10 9.1-10 9.1Z"
        strokeLinejoin="round"
      />
    </svg>
  );
}
