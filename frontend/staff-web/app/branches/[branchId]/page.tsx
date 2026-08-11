"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  createTable,
  getActiveQrToken,
  listTables,
  regenerateQrToken,
  revokeQrToken,
  type QrToken,
  type StaffTable,
} from "@/lib/api";
import StaffNav from "@/components/layout/StaffNav";
import Button from "@/components/ui/Button";
import styles from "@/styles/admin.module.css";

/** Section 4, staff-web admin screen: Table + QR token management (Permission.BRANCH_MANAGE / QR_MANAGE). */
export default function BranchDetailPage() {
  const params = useParams<{ branchId: string }>();
  const branchId = params.branchId;

  const [tables, setTables] = useState<StaffTable[]>([]);
  const [qrTokens, setQrTokens] = useState<Record<string, QrToken | null>>({});
  const [loading, setLoading] = useState(true);
  const [label, setLabel] = useState("");
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busyTableId, setBusyTableId] = useState<string | null>(null);

  async function reload() {
    try {
      const tableList = await listTables(branchId);
      setTables(tableList);
      const tokens = await Promise.all(tableList.map((table) => getActiveQrToken(branchId, table.id)));
      setQrTokens(Object.fromEntries(tableList.map((table, index) => [table.id, tokens[index]])));
    } catch {
      setError("Masalar yüklenemedi.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    async function fetchTables() {
      try {
        const tableList = await listTables(branchId);
        const tokens = await Promise.all(tableList.map((table) => getActiveQrToken(branchId, table.id)));
        if (!cancelled) {
          setTables(tableList);
          setQrTokens(Object.fromEntries(tableList.map((table, index) => [table.id, tokens[index]])));
        }
      } catch {
        if (!cancelled) {
          setError("Masalar yüklenemedi.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void fetchTables();
    return () => {
      cancelled = true;
    };
  }, [branchId]);

  async function handleCreateTable(event: React.FormEvent) {
    event.preventDefault();
    if (!label.trim()) {
      return;
    }
    setCreating(true);
    setError(null);
    try {
      await createTable(branchId, label.trim());
      setLabel("");
      await reload();
    } catch {
      setError("Masa oluşturulamadı.");
    } finally {
      setCreating(false);
    }
  }

  async function handleRegenerate(tableId: string) {
    setBusyTableId(tableId);
    setError(null);
    try {
      const token = await regenerateQrToken(branchId, tableId);
      setQrTokens((current) => ({ ...current, [tableId]: token }));
    } catch {
      setError("QR kod üretilemedi.");
    } finally {
      setBusyTableId(null);
    }
  }

  async function handleRevoke(tableId: string, qrTokenId: string) {
    setBusyTableId(tableId);
    setError(null);
    try {
      await revokeQrToken(qrTokenId);
      setQrTokens((current) => ({ ...current, [tableId]: null }));
    } catch {
      setError("QR kod iptal edilemedi.");
    } finally {
      setBusyTableId(null);
    }
  }

  return (
    <>
      <StaffNav />
      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>Masalar &amp; QR Kodları</h1>
          <Link href="/branches" className={styles.backLink}>
            Şubelere dön
          </Link>
        </div>

        <form className={styles.form} onSubmit={handleCreateTable}>
          <div className={styles.field}>
            <label className={styles.label} htmlFor="table-label">
              Yeni masa adı
            </label>
            <input id="table-label" className={styles.input} value={label} onChange={(event) => setLabel(event.target.value)} required />
          </div>
          <Button type="submit" disabled={creating}>
            {creating ? "Oluşturuluyor…" : "Masa Ekle"}
          </Button>
        </form>

        {error ? <p className={styles.error}>{error}</p> : null}

        <div className={styles.list}>
          {loading ? (
            <p className={styles.empty}>Yükleniyor…</p>
          ) : tables.length === 0 ? (
            <p className={styles.empty}>Henüz masa yok.</p>
          ) : (
            tables.map((table) => {
              const token = qrTokens[table.id];
              return (
                <div key={table.id} className={styles.row}>
                  <div className={styles.rowMain}>
                    <span className={styles.rowTitle}>{table.label}</span>
                    <span className={styles.qrToken}>{token ? token.token : "Aktif QR kodu yok"}</span>
                  </div>
                  <div className={styles.rowActions}>
                    <Button size="md" variant="secondary" disabled={busyTableId === table.id} onClick={() => handleRegenerate(table.id)}>
                      {token ? "Yeniden Üret" : "QR Üret"}
                    </Button>
                    {token ? (
                      <Button size="md" variant="ghost" disabled={busyTableId === table.id} onClick={() => handleRevoke(table.id, token.id)}>
                        İptal Et
                      </Button>
                    ) : null}
                  </div>
                </div>
              );
            })
          )}
        </div>
      </main>
    </>
  );
}
