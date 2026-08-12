"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  createTable,
  getActiveQrToken,
  getBusinessHours,
  listBranches,
  listTables,
  regenerateQrToken,
  revokeQrToken,
  setAddress,
  setBranchTimezone,
  setBusinessHours,
  type Branch,
  type BranchBusinessHoursEntry,
  type DayOfWeek,
  type QrToken,
  type StaffTable,
} from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import Button from "@/components/ui/Button";
import styles from "@/styles/admin.module.css";

const DAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: "Pazartesi",
  TUESDAY: "Salı",
  WEDNESDAY: "Çarşamba",
  THURSDAY: "Perşembe",
  FRIDAY: "Cuma",
  SATURDAY: "Cumartesi",
  SUNDAY: "Pazar",
};
const DAYS_OF_WEEK: DayOfWeek[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

function defaultHoursForDay(dayOfWeek: DayOfWeek): BranchBusinessHoursEntry {
  return { dayOfWeek, openingTime: null, closingTime: null, closed: false };
}

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

  const [branch, setBranch] = useState<Branch | null>(null);
  const [addressInput, setAddressInput] = useState("");
  const [timezoneInput, setTimezoneInput] = useState("");
  const [hours, setHours] = useState<BranchBusinessHoursEntry[]>(DAYS_OF_WEEK.map(defaultHoursForDay));
  const [settingsError, setSettingsError] = useState<string | null>(null);
  const [settingsSuccess, setSettingsSuccess] = useState<string | null>(null);
  const [savingAddress, setSavingAddress] = useState(false);
  const [savingTimezone, setSavingTimezone] = useState(false);
  const [savingHours, setSavingHours] = useState(false);

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

  useEffect(() => {
    let cancelled = false;
    async function fetchSettings() {
      try {
        const [branches, hoursList] = await Promise.all([listBranches(), getBusinessHours(branchId)]);
        if (cancelled) {
          return;
        }
        const current = branches.find((b) => b.id === branchId) ?? null;
        setBranch(current);
        setAddressInput(current?.address ?? "");
        setTimezoneInput(current?.timezone ?? "");
        const byDay = Object.fromEntries(hoursList.map((entry) => [entry.dayOfWeek, entry]));
        setHours(DAYS_OF_WEEK.map((day) => byDay[day] ?? defaultHoursForDay(day)));
      } catch {
        if (!cancelled) {
          setSettingsError("Şube ayarları yüklenemedi.");
        }
      }
    }
    void fetchSettings();
    return () => {
      cancelled = true;
    };
  }, [branchId]);

  async function handleSaveAddress(event: React.FormEvent) {
    event.preventDefault();
    setSavingAddress(true);
    setSettingsError(null);
    setSettingsSuccess(null);
    try {
      const updated = await setAddress(branchId, addressInput.trim());
      setBranch(updated);
      setSettingsSuccess("Adres kaydedildi.");
    } catch {
      setSettingsError("Adres kaydedilemedi.");
    } finally {
      setSavingAddress(false);
    }
  }

  async function handleSaveTimezone(event: React.FormEvent) {
    event.preventDefault();
    setSavingTimezone(true);
    setSettingsError(null);
    setSettingsSuccess(null);
    try {
      const updated = await setBranchTimezone(branchId, timezoneInput.trim() || null);
      setBranch(updated);
      setSettingsSuccess("Saat dilimi kaydedildi.");
    } catch {
      setSettingsError("Saat dilimi kaydedilemedi. Geçerli bir IANA saat dilimi kimliği girin (ör. Europe/Istanbul).");
    } finally {
      setSavingTimezone(false);
    }
  }

  async function handleSaveHours() {
    setSavingHours(true);
    setSettingsError(null);
    setSettingsSuccess(null);
    try {
      const updated = await setBusinessHours(branchId, hours);
      setHours(updated);
      setSettingsSuccess("Çalışma saatleri kaydedildi.");
    } catch {
      setSettingsError("Çalışma saatleri kaydedilemedi.");
    } finally {
      setSavingHours(false);
    }
  }

  function updateDay(dayOfWeek: DayOfWeek, patch: Partial<BranchBusinessHoursEntry>) {
    setHours((current) => current.map((entry) => (entry.dayOfWeek === dayOfWeek ? { ...entry, ...patch } : entry)));
  }

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
    <AppShell>
      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>{branch ? branch.name : "Şube"}</h1>
          <Link href="/branches" className={styles.backLink}>
            Şubelere dön
          </Link>
        </div>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Şube Ayarları</h2>
          {settingsError ? <p className={styles.error}>{settingsError}</p> : null}
          {settingsSuccess ? <p className={styles.success}>{settingsSuccess}</p> : null}

          <form className={styles.form} onSubmit={handleSaveAddress}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="branch-address">
                Adres
              </label>
              <input
                id="branch-address"
                className={styles.input}
                value={addressInput}
                onChange={(event) => setAddressInput(event.target.value)}
              />
            </div>
            <Button type="submit" disabled={savingAddress}>
              {savingAddress ? "Kaydediliyor…" : "Adresi Kaydet"}
            </Button>
          </form>

          <form className={styles.form} onSubmit={handleSaveTimezone}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="branch-timezone">
                Saat Dilimi (opsiyonel - boş bırakılırsa işletme varsayılanı kullanılır)
              </label>
              <input
                id="branch-timezone"
                className={styles.input}
                placeholder="Europe/Istanbul"
                value={timezoneInput}
                onChange={(event) => setTimezoneInput(event.target.value)}
              />
            </div>
            <Button type="submit" disabled={savingTimezone}>
              {savingTimezone ? "Kaydediliyor…" : "Saat Dilimini Kaydet"}
            </Button>
          </form>

          <div className={styles.list}>
            {hours.map((entry) => (
              <div key={entry.dayOfWeek} className={styles.row}>
                <div className={styles.rowMain}>
                  <span className={styles.rowTitle}>{DAY_LABELS[entry.dayOfWeek]}</span>
                </div>
                <div className={styles.rowActions}>
                  <label className={styles.rowMeta}>
                    <input
                      type="checkbox"
                      checked={entry.closed}
                      onChange={(event) => updateDay(entry.dayOfWeek, { closed: event.target.checked })}
                    />{" "}
                    Kapalı
                  </label>
                  <input
                    type="time"
                    className={styles.input}
                    disabled={entry.closed}
                    value={entry.openingTime?.slice(0, 5) ?? ""}
                    onChange={(event) => updateDay(entry.dayOfWeek, { openingTime: event.target.value || null })}
                  />
                  <span className={styles.rowMeta}>–</span>
                  <input
                    type="time"
                    className={styles.input}
                    disabled={entry.closed}
                    value={entry.closingTime?.slice(0, 5) ?? ""}
                    onChange={(event) => updateDay(entry.dayOfWeek, { closingTime: event.target.value || null })}
                  />
                </div>
              </div>
            ))}
          </div>
          <Button disabled={savingHours} onClick={handleSaveHours}>
            {savingHours ? "Kaydediliyor…" : "Çalışma Saatlerini Kaydet"}
          </Button>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Masalar &amp; QR Kodları</h2>
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
        </section>
      </main>
    </AppShell>
  );
}
