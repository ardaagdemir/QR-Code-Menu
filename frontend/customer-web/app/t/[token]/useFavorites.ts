"use client";

import { useCallback, useEffect, useState } from "react";

function storageKey(branchId: string): string {
  return `qrmenu.favorites.${branchId}`;
}

function readStoredIds(branchId: string): string[] {
  if (typeof window === "undefined" || !branchId) {
    return [];
  }
  try {
    const raw = window.localStorage.getItem(storageKey(branchId));
    if (!raw) {
      return [];
    }
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((id): id is string => typeof id === "string") : [];
  } catch {
    return [];
  }
}

function loadValidFavorites(branchId: string, validProductIds: Set<string>): Set<string> {
  return new Set(readStoredIds(branchId).filter((id) => validProductIds.has(id)));
}

/** Backend'de müşteri favorisi kavramı yok - branch bazlı localStorage'da tutulur ve
 * kalıcıdır. Menüde artık bulunmayan (silinmiş/opt-out edilmiş) ürün id'leri, geçerli
 * ürün kümesiyle karşılaştırılıp sessizce elenir. branchId değişimine render sırasında
 * (React'in "adjusting state on prop change" deseniyle) tepki verilir - localStorage'a
 * yazma ise ayrı bir efektte, harici sistemle senkronizasyon olarak yapılır. */
export function useFavorites(branchId: string, validProductIds: Set<string>) {
  const [trackedBranchId, setTrackedBranchId] = useState(branchId);
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(() => loadValidFavorites(branchId, validProductIds));

  if (branchId !== trackedBranchId) {
    setTrackedBranchId(branchId);
    setFavoriteIds(loadValidFavorites(branchId, validProductIds));
  }

  useEffect(() => {
    if (!branchId) {
      return;
    }
    window.localStorage.setItem(storageKey(branchId), JSON.stringify(Array.from(favoriteIds)));
  }, [branchId, favoriteIds]);

  const toggleFavorite = useCallback((productId: string) => {
    setFavoriteIds((current) => {
      const next = new Set(current);
      if (next.has(productId)) {
        next.delete(productId);
      } else {
        next.add(productId);
      }
      return next;
    });
  }, []);

  return { favoriteIds, toggleFavorite };
}
