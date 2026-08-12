type Props = {
  size?: number;
  className?: string;
};

/** Nötr, tasarım diliyle uyumlu placeholder - ürün görseli yokken veya menü boşken
 * emoji yerine kullanılır (docs/product-requirements.md Bölüm 19.1/19.2). */
export default function DishPlaceholderIcon({ size = 28, className }: Props) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="8.5" />
      <circle cx="12" cy="12" r="4.5" />
    </svg>
  );
}
