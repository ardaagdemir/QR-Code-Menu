"use client";

import { useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { buildPickupBoardStreamUrl, getPickupBoard, type PickupBoardEntry } from "@/lib/api";
import styles from "./page.module.css";

/**
 * Section 4, staff-web screen #8: kiosk-mode pickup board for CUSTOMER_PICKUP branches
 * - meant to run unattended on a screen in the restaurant, so no login/nav here (public/
 * unauthenticated endpoint, see PickupBoardController). Same "SSE as a refetch signal"
 * pattern as the KDS board (app/kitchen/[branchId]/page.tsx).
 */
export default function PickupBoardPage() {
  const params = useParams<{ branchId: string }>();
  const branchId = params.branchId;

  const [entries, setEntries] = useState<PickupBoardEntry[]>([]);
  const [connectionStatus, setConnectionStatus] = useState<"connecting" | "live" | "reconnecting">("connecting");
  const latestRequestIdRef = useRef(0);

  useEffect(() => {
    let cancelled = false;

    async function fetchBoard() {
      const requestId = ++latestRequestIdRef.current;
      try {
        const data = await getPickupBoard(branchId);
        if (!cancelled && requestId === latestRequestIdRef.current) {
          setEntries(data);
        }
      } catch {
        // A transient fetch failure just leaves the board showing its last known state
        // - a kiosk screen has no one to show an error message to.
      }
    }

    const eventSource = new EventSource(buildPickupBoardStreamUrl(branchId));
    eventSource.addEventListener("open", () => setConnectionStatus("live"));
    eventSource.addEventListener("error", () => setConnectionStatus("reconnecting"));
    eventSource.addEventListener("order-status", () => fetchBoard());
    void fetchBoard();

    return () => {
      cancelled = true;
      eventSource.close();
    };
  }, [branchId]);

  return (
    <main className={styles.page}>
      <h1 className={styles.title}>Siparişiniz Hazır</h1>
      <span className={styles.connectionStatus}>
        {connectionStatus === "live" ? "Canlı" : connectionStatus === "reconnecting" ? "Yeniden bağlanıyor…" : "Bağlanıyor…"}
      </span>

      {entries.length === 0 ? (
        <p className={styles.empty}>Şu an hazır sipariş yok.</p>
      ) : (
        <div className={styles.grid}>
          {entries.map((entry) => (
            <div key={entry.orderId} className={styles.number}>
              {entry.orderNumber ?? "—"}
            </div>
          ))}
        </div>
      )}
    </main>
  );
}
