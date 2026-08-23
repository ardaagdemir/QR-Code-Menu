"use client";

import { useCallback, useState } from "react";
import { addOrderToken, readOrderHistoryEntries, removeOrderToken, type OrderHistoryEntry } from "./orderHistoryStorage";

/** Kalıcı (localStorage) sipariş takip geçmişini React'e bağlar - masa (tableId) bazlı,
 * useFavorites.ts'deki branch bazlı desenin aynısı: tableId değişimine render sırasında
 * tepki verilir, bu yüzden her (yeniden) mount'ta güncel liste doğrudan localStorage'dan
 * okunur ve geçici React state'ine hiç güvenilmez. tableId (branchId değil) kullanılır
 * çünkü aynı branch'teki farklı masaların sipariş geçmişi birbirinden izole olmalı (Masa
 * 8'deki sipariş Masa 9'da görünmemeli). */
export function useOrderHistory(tableId: string) {
  const [trackedTableId, setTrackedTableId] = useState(tableId);
  const [entries, setEntries] = useState<OrderHistoryEntry[]>(() => readOrderHistoryEntries(tableId));

  if (tableId !== trackedTableId) {
    setTrackedTableId(tableId);
    setEntries(readOrderHistoryEntries(tableId));
  }

  const addToken = useCallback(
    (token: string) => {
      if (!tableId) {
        return;
      }
      addOrderToken(tableId, token);
      setEntries(readOrderHistoryEntries(tableId));
    },
    [tableId],
  );

  const removeToken = useCallback(
    (token: string) => {
      if (!tableId) {
        return;
      }
      removeOrderToken(tableId, token);
      setEntries((current) => current.filter((entry) => entry.token !== token));
    },
    [tableId],
  );

  return { entries, addToken, removeToken };
}
