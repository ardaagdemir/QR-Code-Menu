"use client";

import { useEffect, useState } from "react";
import { listAuditEntries, listStaffUsers, type AuditEntry, type StaffUser } from "@/lib/api";
import StaffNav from "@/components/layout/StaffNav";
import styles from "@/styles/admin.module.css";

/** Section 4, staff-web admin screen #7: "Sipariş/Ödeme/İade geçmişi ve audit görünümü" (Permission.AUDIT_VIEW). */
export default function AuditPage() {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [staffUsers, setStaffUsers] = useState<StaffUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([listAuditEntries(), listStaffUsers()])
      .then(([auditEntries, users]) => {
        setEntries(auditEntries);
        setStaffUsers(users);
      })
      .catch(() => setError("Denetim kaydı yüklenemedi."))
      .finally(() => setLoading(false));
  }, []);

  function actorLabel(actorStaffUserId: string | null): string {
    if (!actorStaffUserId) {
      return "Sistem";
    }
    return staffUsers.find((u) => u.id === actorStaffUserId)?.email ?? actorStaffUserId;
  }

  return (
    <>
      <StaffNav />
      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>Denetim Kaydı</h1>
        </div>

        {error ? <p className={styles.error}>{error}</p> : null}

        <div className={styles.list}>
          {loading ? (
            <p className={styles.empty}>Yükleniyor…</p>
          ) : entries.length === 0 ? (
            <p className={styles.empty}>Henüz kayıt yok.</p>
          ) : (
            entries.map((entry) => (
              <div key={entry.id} className={styles.row}>
                <div className={styles.rowMain}>
                  <span className={styles.rowTitle}>
                    {entry.entityType} · {entry.action}
                  </span>
                  <span className={styles.rowMeta}>
                    {actorLabel(entry.actorStaffUserId)} · {new Date(entry.createdAt).toLocaleString("tr-TR")}
                  </span>
                  {entry.details ? <span className={styles.qrToken}>{entry.details}</span> : null}
                </div>
              </div>
            ))
          )}
        </div>
      </main>
    </>
  );
}
