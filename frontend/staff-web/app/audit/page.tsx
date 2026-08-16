"use client";

import { useEffect, useState } from "react";
import { listAuditEntries, listStaffUsers, type AuditEntry, type StaffUser } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";

/** Audit entries returned by the backend are restricted to the active branch. */
export default function AuditPage() {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [staffUsers, setStaffUsers] = useState<StaffUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  function load() {
    Promise.all([listAuditEntries(), listStaffUsers()])
      .then(([auditEntries, users]) => {
        setEntries(auditEntries);
        setStaffUsers(users);
        setError(null);
      })
      .catch(() => setError("Denetim kaydı yüklenemedi."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  function actorLabel(actorStaffUserId: string | null): string {
    if (!actorStaffUserId) return "Sistem";
    return staffUsers.find((user) => user.id === actorStaffUserId)?.email ?? actorStaffUserId;
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader title="Denetim Kaydı" description="Aktif şubenizdeki kritik işlemlerin denetim izi." />
        {loading ? <TableSkeleton /> : error ? (
          <ErrorState message={error} onRetry={load} />
        ) : entries.length === 0 ? (
          <EmptyState title="Henüz kayıt yok" />
        ) : (
          <Table>
            <thead><tr><th>Tarih</th><th>Aktör</th><th>Varlık</th><th>Aksiyon</th><th>Detay</th></tr></thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.id}>
                  <td className={tableStyles.muted}>{new Date(entry.createdAt).toLocaleString("tr-TR")}</td>
                  <td>{actorLabel(entry.actorStaffUserId)}</td>
                  <td>{entry.entityType}</td>
                  <td>{entry.action}</td>
                  <td className={tableStyles.muted}>{entry.details ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </main>
    </AppShell>
  );
}
