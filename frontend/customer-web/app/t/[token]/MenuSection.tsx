import type { MenuCategory, MenuProduct } from "@/lib/api";
import ProductCard from "./ProductCard";
import styles from "./MenuSection.module.css";

type Props = {
  category: MenuCategory;
  onSelectProduct: (product: MenuProduct) => void;
};

export default function MenuSection({ category, onSelectProduct }: Props) {
  const headingId = `category-heading-${category.id}`;
  return (
    <section id={`category-${category.id}`} className={styles.section} aria-labelledby={headingId}>
      <h2 id={headingId} className={styles.heading}>
        {category.name}
      </h2>
      <ul className={styles.list}>
        {category.products.map((product) => (
          <ProductCard key={product.id} product={product} onSelect={onSelectProduct} />
        ))}
      </ul>
    </section>
  );
}
