"use client";

import { useEffect, useRef } from "react";
import type { MenuCategory } from "@/lib/api";
import styles from "./CategoryNav.module.css";

type Props = {
  categories: MenuCategory[];
  activeCategoryId: string | null;
  onSelect: (categoryId: string) => void;
};

/** Sticky, horizontally scrollable category shortcuts - keeps the active chip in view
 * as the user scrolls through the menu. */
export default function CategoryNav({ categories, activeCategoryId, onSelect }: Props) {
  const navRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!activeCategoryId || !navRef.current) {
      return;
    }
    const activeChip = navRef.current.querySelector<HTMLElement>(`[data-category-id="${activeCategoryId}"]`);
    activeChip?.scrollIntoView({ behavior: "smooth", inline: "center", block: "nearest" });
  }, [activeCategoryId]);

  if (categories.length <= 1) {
    return null;
  }

  return (
    <nav className={styles.nav} ref={navRef} aria-label="Menü kategorileri">
      {categories.map((category) => {
        const isActive = category.id === activeCategoryId;
        return (
          <button
            key={category.id}
            type="button"
            data-category-id={category.id}
            className={[styles.chip, isActive ? styles.chipActive : ""].filter(Boolean).join(" ")}
            aria-current={isActive ? "true" : undefined}
            onClick={() => onSelect(category.id)}
          >
            {category.name}
          </button>
        );
      })}
    </nav>
  );
}
