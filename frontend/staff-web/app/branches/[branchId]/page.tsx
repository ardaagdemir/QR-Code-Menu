"use client";

import { useEffect, useId, useState } from "react";
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
  setStoreAcceptanceTimeout,
  type Branch,
  type BranchBusinessHoursEntry,
  type DayOfWeek,
  type QrToken,
  type StaffTable,
} from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import { useToast } from "@/components/ui/ToastProvider";
import tableStyles from "@/components/ui/Table.module.css";
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
  const { showToast } = useToast();
  const createTableDialogTitleId = useId();

  const [tables, setTables] = useState<StaffTable[]>([]);
  const [qrTokens, setQrTokens] = useState<Record<string, QrToken | null>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyTableId, setBusyTableId] = useState<string | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<{ tableId: string; qrTokenId: string; label: string } | null>(null);

  const [createTableOpen, setCreateTableOpen] = useState(false);
  const [label, setLabel] = useState("");
  const [creating, setCreating] = useState(false);

  const [branch, setBranch] = useState<Branch | null>(null);
  const [addressInput, setAddressInput] = useState("");
  const [timezoneInput, setTimezoneInput] = useState("");
  const [timeoutMinutesInput, setTimeoutMinutesInput] = useState("5");
  const [hours, setHours] = useState<BranchBusinessHoursEntry[]>(DAYS_OF_WEEK.map(defaultHoursForDay));
  const [settingsError, setSettingsError] = useState<string | null>(null);
  const [savingAddress, setSavingAddress] = useState(false);
  const [savingTimezone, setSavingTimezone] = useState(false);
  const [savingTimeout, setSavingTimeout] = useState(false);
  const [savingHours, setSavingHours] = useState(false);

  function loadTables() {
    listTables(branchId)
      .then(async (tableList) => {
        const tokens = await Promise.all(tableList.map((table) => getActiveQrToken(branchId, table.id)));
        setTables(tableList);
        setQrTokens(Object.fromEntries(tableList.map((table, index) => [table.id, tokens[index]])));
        setError(null);
      })
      .catch(() => setError("Masalar yüklenemedi."))
      .finally(() => setLoading(false));
  }

  useEffect(loadTables, [branchId]);

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
        setTimeoutMinutesInput(current ? String(Math.round(current.storeAcceptanceTimeoutSeconds / 60)) : "5");
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
    try {
      const updated = await setAddress(branchId, addressInput.trim());
      setBranch(updated);
      showToast("Adres kaydedildi.", "success");
    } catch {
      showToast("Adres kaydedilemedi.", "error");
    } finally {
      setSavingAddress(false);
    }
  }

  async function handleSaveTimezone(event: React.FormEvent) {
    event.preventDefault();
    setSavingTimezone(true);
    try {
      const updated = await setBranchTimezone(branchId, timezoneInput.trim() || null);
      setBranch(updated);
      showToast("Saat dilimi kaydedildi.", "success");
    } catch {
      showToast("Saat dilimi kaydedilemedi. Geçerli bir IANA saat dilimi kimliği girin (ör. Europe/Istanbul).", "error");
    } finally {
      setSavingTimezone(false);
    }
  }

  /** Section 6/27: kasa kabul bekleme timeout'u dakika olarak girilir, backend'e saniye olarak gönderilir. */
  async function handleSaveTimeout(event: React.FormEvent) {
    event.preventDefault();
    const minutes = Number(timeoutMinutesInput);
    if (!Number.isFinite(minutes) || minutes <= 0) {
      showToast("Geçerli bir dakika değeri girin (0'dan büyük).", "error");
      return;
    }
    setSavingTimeout(true);
    try {
      const updated = await setStoreAcceptanceTimeout(branchId, Math.round(minutes * 60));
      setBranch(updated);
      showToast("Kasa kabul bekleme süresi kaydedildi.", "success");
    } catch {
      showToast("Kasa kabul bekleme süresi kaydedilemedi.", "error");
    } finally {
      setSavingTimeout(false);
    }
  }

  async function handleSaveHours() {
    setSavingHours(true);
    try {
      const updated = await setBusinessHours(branchId, hours);
      setHours(updated);
      showToast("Çalışma saatleri kaydedildi.", "success");
    } catch {
      showToast("Çalışma saatleri kaydedilemedi.", "error");
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
    try {
      await createTable(branchId, label.trim());
      setLabel("");
      setCreateTableOpen(false);
      loadTables();
      showToast("Masa oluşturuldu.", "success");
    } catch {
      showToast("Masa oluşturulamadı.", "error");
    } finally {
      setCreating(false);
    }
  }

  async function handleRegenerate(tableId: string) {
    setBusyTableId(tableId);
    try {
      const token = await regenerateQrToken(branchId, tableId);
      setQrTokens((current) => ({ ...current, [tableId]: token }));
      showToast("QR kod üretildi.", "success");
    } catch {
      showToast("QR kod üretilemedi.", "error");
    } finally {
      setBusyTableId(null);
    }
  }

  async function handleConfirmRevoke() {
    if (!revokeTarget) {
      return;
    }
    setBusyTableId(revokeTarget.tableId);
    try {
      await revokeQrToken(revokeTarget.qrTokenId);
      setQrTokens((current) => ({ ...current, [revokeTarget.tableId]: null }));
      showToast("QR kod iptal edildi.", "success");
    } catch {
      showToast("QR kod iptal edilemedi.", "error");
    } finally {
      setBusyTableId(null);
      setRevokeTarget(null);
    }
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader
          title={branch ? branch.name : "Şube"}
          actions={
            <Link href="/branches" className={styles.backLink}>
              Şubelere dön
            </Link>
          }
        />

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Şube Ayarları</h2>
          {settingsError ? <ErrorState message={settingsError} /> : null}

          <form className={styles.form} onSubmit={handleSaveAddress}>
            <FormField label="Adres">
              {(controlProps) => <Input {...controlProps} value={addressInput} onChange={(event) => setAddressInput(event.target.value)} />}
            </FormField>
            <Button type="submit" disabled={savingAddress}>
              {savingAddress ? "Kaydediliyor…" : "Adresi Kaydet"}
            </Button>
          </form>

          <form className={styles.form} onSubmit={handleSaveTimezone}>
            <FormField label="Saat Dilimi (opsiyonel - boş bırakılırsa işletme varsayılanı kullanılır)">
              {(controlProps) => (
                <Input
                  {...controlProps}
                  placeholder="Europe/Istanbul"
                  value={timezoneInput}
                  onChange={(event) => setTimezoneInput(event.target.value)}
                />
              )}
            </FormField>
            <Button type="submit" disabled={savingTimezone}>
              {savingTimezone ? "Kaydediliyor…" : "Saat Dilimini Kaydet"}
            </Button>
          </form>

          <form className={styles.form} onSubmit={handleSaveTimeout}>
            <FormField label="Kasa Kabul Bekleme Süresi (dakika) - bu süre dolunca sipariş kasa ekranında kritik olarak işaretlenir">
              {(controlProps) => (
                <Input
                  {...controlProps}
                  type="number"
                  min={1}
                  step={1}
                  value={timeoutMinutesInput}
                  onChange={(event) => setTimeoutMinutesInput(event.target.value)}
                />
              )}
            </FormField>
            <Button type="submit" disabled={savingTimeout}>
              {savingTimeout ? "Kaydediliyor…" : "Bekleme Süresini Kaydet"}
            </Button>
          </form>

          <Table>
            <thead>
              <tr>
                <th>Gün</th>
                <th>Kapalı</th>
                <th>Açılış</th>
                <th>Kapanış</th>
              </tr>
            </thead>
            <tbody>
              {hours.map((entry) => (
                <tr key={entry.dayOfWeek}>
                  <td className={tableStyles.primary}>{DAY_LABELS[entry.dayOfWeek]}</td>
                  <td>
                    <input
                      type="checkbox"
                      checked={entry.closed}
                      onChange={(event) => updateDay(entry.dayOfWeek, { closed: event.target.checked })}
                    />
                  </td>
                  <td>
                    <input
                      type="time"
                      className={styles.input}
                      disabled={entry.closed}
                      value={entry.openingTime?.slice(0, 5) ?? ""}
                      onChange={(event) => updateDay(entry.dayOfWeek, { openingTime: event.target.value || null })}
                    />
                  </td>
                  <td>
                    <input
                      type="time"
                      className={styles.input}
                      disabled={entry.closed}
                      value={entry.closingTime?.slice(0, 5) ?? ""}
                      onChange={(event) => updateDay(entry.dayOfWeek, { closingTime: event.target.value || null })}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
          <Button disabled={savingHours} onClick={handleSaveHours}>
            {savingHours ? "Kaydediliyor…" : "Çalışma Saatlerini Kaydet"}
          </Button>
        </section>

        <section className={styles.section}>
          <PageHeader title="Masalar & QR Kodları" actions={<Button onClick={() => setCreateTableOpen(true)}>+ Masa Ekle</Button>} />

          {loading ? (
            <TableSkeleton />
          ) : error ? (
            <ErrorState message={error} onRetry={loadTables} />
          ) : tables.length === 0 ? (
            <EmptyState title="Henüz masa yok" />
          ) : (
            <Table>
              <thead>
                <tr>
                  <th>Masa</th>
                  <th>QR Token</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {tables.map((table) => {
                  const token = qrTokens[table.id];
                  return (
                    <tr key={table.id}>
                      <td className={tableStyles.primary}>{table.label}</td>
                      <td className={`${tableStyles.muted} ${styles.qrToken}`}>{token ? token.token : "Aktif QR kodu yok"}</td>
                      <td>
                        <div className={tableStyles.actions}>
                          <Button size="md" variant="secondary" disabled={busyTableId === table.id} onClick={() => handleRegenerate(table.id)}>
                            {token ? "Yeniden Üret" : "QR Üret"}
                          </Button>
                          {token ? (
                            <Button
                              size="md"
                              variant="ghost"
                              disabled={busyTableId === table.id}
                              onClick={() => setRevokeTarget({ tableId: table.id, qrTokenId: token.id, label: table.label })}
                            >
                              İptal Et
                            </Button>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>
          )}
        </section>
      </main>

      {createTableOpen ? (
        <Dialog onClose={() => setCreateTableOpen(false)} labelledBy={createTableDialogTitleId}>
          <h2 id={createTableDialogTitleId} className={styles.sectionTitle}>
            Yeni Masa
          </h2>
          <form className={styles.section} onSubmit={handleCreateTable}>
            <FormField label="Masa adı" required>
              {(controlProps) => <Input {...controlProps} value={label} onChange={(event) => setLabel(event.target.value)} required />}
            </FormField>
            <Button type="submit" disabled={creating}>
              {creating ? "Oluşturuluyor…" : "Masa Ekle"}
            </Button>
          </form>
        </Dialog>
      ) : null}

      {revokeTarget ? (
        <ConfirmDialog
          title="QR Kodu İptal Et"
          message={`"${revokeTarget.label}" masasının QR kodu iptal edilecek. Bu masadaki fiziksel QR etiketi artık çalışmayacak.`}
          confirmLabel="İptal Et"
          tone="danger"
          confirmLoading={busyTableId === revokeTarget.tableId}
          onConfirm={handleConfirmRevoke}
          onCancel={() => setRevokeTarget(null)}
        />
      ) : null}
    </AppShell>
  );
}
