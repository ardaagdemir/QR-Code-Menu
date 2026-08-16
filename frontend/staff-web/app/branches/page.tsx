"use client";

import { useEffect, useState } from "react";
import {
  getBusinessHours,
  listBranches,
  setAddress,
  setBranchTimezone,
  setBusinessHours,
  setDeliveryModel,
  setOrderingEnabled,
  setStoreAcceptanceTimeout,
  type Branch,
  type BranchBusinessHoursEntry,
  type DayOfWeek,
  type DeliveryModel,
} from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import ErrorState from "@/components/ui/ErrorState";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import PageHeader from "@/components/ui/PageHeader";
import Select from "@/components/ui/Select";
import Table from "@/components/ui/Table";
import TableSkeleton from "@/components/ui/TableSkeleton";
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

export default function BranchSettingsPage() {
  const { showToast } = useToast();
  const [branch, setBranch] = useState<Branch | null>(null);
  const [addressInput, setAddressInput] = useState("");
  const [timezoneInput, setTimezoneInput] = useState("");
  const [timeoutMinutesInput, setTimeoutMinutesInput] = useState("5");
  const [deliveryModel, setDeliveryModelInput] = useState<DeliveryModel>("WAITER_DELIVERY");
  const [hours, setHours] = useState<BranchBusinessHoursEntry[]>(DAYS_OF_WEEK.map(defaultHoursForDay));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([listBranches(), getBusinessHours()])
      .then(([branches, hoursList]) => {
        if (cancelled) return;
        const current = branches[0] ?? null;
        setBranch(current);
        setAddressInput(current?.address ?? "");
        setTimezoneInput(current?.timezone ?? "");
        setTimeoutMinutesInput(current ? String(Math.round(current.storeAcceptanceTimeoutSeconds / 60)) : "5");
        setDeliveryModelInput(current?.deliveryModel ?? "WAITER_DELIVERY");
        const byDay = Object.fromEntries(hoursList.map((entry) => [entry.dayOfWeek, entry]));
        setHours(DAYS_OF_WEEK.map((day) => byDay[day] ?? defaultHoursForDay(day)));
        setError(null);
      })
      .catch(() => {
        if (!cancelled) setError("Şube ayarları yüklenemedi.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleToggleOrdering() {
    if (!branch) return;
    setSaving("ordering");
    try {
      setBranch(await setOrderingEnabled(!branch.orderingEnabled));
      showToast("Sipariş durumu güncellendi.", "success");
    } catch {
      showToast("Sipariş durumu güncellenemedi.", "error");
    } finally {
      setSaving(null);
    }
  }

  async function handleSaveDeliveryModel(event: React.FormEvent) {
    event.preventDefault();
    setSaving("delivery");
    try {
      setBranch(await setDeliveryModel(deliveryModel));
      showToast("Teslimat modeli kaydedildi.", "success");
    } catch {
      showToast("Teslimat modeli kaydedilemedi.", "error");
    } finally {
      setSaving(null);
    }
  }

  async function handleSaveAddress(event: React.FormEvent) {
    event.preventDefault();
    setSaving("address");
    try {
      setBranch(await setAddress(addressInput.trim()));
      showToast("Adres kaydedildi.", "success");
    } catch {
      showToast("Adres kaydedilemedi.", "error");
    } finally {
      setSaving(null);
    }
  }

  async function handleSaveTimezone(event: React.FormEvent) {
    event.preventDefault();
    setSaving("timezone");
    try {
      setBranch(await setBranchTimezone(timezoneInput.trim() || null));
      showToast("Saat dilimi kaydedildi.", "success");
    } catch {
      showToast("Saat dilimi kaydedilemedi. Geçerli bir IANA saat dilimi girin.", "error");
    } finally {
      setSaving(null);
    }
  }

  async function handleSaveTimeout(event: React.FormEvent) {
    event.preventDefault();
    const minutes = Number(timeoutMinutesInput);
    if (!Number.isFinite(minutes) || minutes <= 0) {
      showToast("Geçerli bir dakika değeri girin.", "error");
      return;
    }
    setSaving("timeout");
    try {
      setBranch(await setStoreAcceptanceTimeout(Math.round(minutes * 60)));
      showToast("Kasa kabul bekleme süresi kaydedildi.", "success");
    } catch {
      showToast("Kasa kabul bekleme süresi kaydedilemedi.", "error");
    } finally {
      setSaving(null);
    }
  }

  async function handleSaveHours() {
    setSaving("hours");
    try {
      setHours(await setBusinessHours(hours));
      showToast("Çalışma saatleri kaydedildi.", "success");
    } catch {
      showToast("Çalışma saatleri kaydedilemedi.", "error");
    } finally {
      setSaving(null);
    }
  }

  function updateDay(dayOfWeek: DayOfWeek, patch: Partial<BranchBusinessHoursEntry>) {
    setHours((current) => current.map((entry) => (entry.dayOfWeek === dayOfWeek ? { ...entry, ...patch } : entry)));
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader title="Şube Ayarları" description={branch?.name ?? "Aktif şube ayarları"} />

        {loading ? <TableSkeleton /> : error ? <ErrorState message={error} /> : null}

        {!loading && !error && branch ? (
          <>
            <section className={styles.section}>
              <h2 className={styles.sectionTitle}>Operasyon</h2>
              <div className={styles.rowActions}>
                <Badge tone={branch.orderingEnabled ? "neutral" : "danger"}>
                  Sipariş {branch.orderingEnabled ? "açık" : "kapalı"}
                </Badge>
                <Button variant="secondary" disabled={saving === "ordering"} onClick={handleToggleOrdering}>
                  {branch.orderingEnabled ? "Siparişi Kapat" : "Siparişi Aç"}
                </Button>
              </div>
              <form className={styles.form} onSubmit={handleSaveDeliveryModel}>
                <FormField label="Teslimat modeli">
                  {(controlProps) => (
                    <Select
                      {...controlProps}
                      value={deliveryModel}
                      onChange={(event) => setDeliveryModelInput(event.target.value as DeliveryModel)}
                    >
                      <option value="WAITER_DELIVERY">Garson servisi</option>
                      <option value="CUSTOMER_PICKUP">Müşteri kendi alır (pickup)</option>
                    </Select>
                  )}
                </FormField>
                <Button type="submit" disabled={saving === "delivery"}>Teslimat Modelini Kaydet</Button>
              </form>
            </section>

            <section className={styles.section}>
              <h2 className={styles.sectionTitle}>Şube Bilgileri</h2>
              <form className={styles.form} onSubmit={handleSaveAddress}>
                <FormField label="Adres">
                  {(controlProps) => <Input {...controlProps} value={addressInput} onChange={(event) => setAddressInput(event.target.value)} />}
                </FormField>
                <Button type="submit" disabled={saving === "address"}>Adresi Kaydet</Button>
              </form>
              <form className={styles.form} onSubmit={handleSaveTimezone}>
                <FormField label="Saat dilimi (boşsa işletme varsayılanı)">
                  {(controlProps) => (
                    <Input {...controlProps} placeholder="Europe/Istanbul" value={timezoneInput} onChange={(event) => setTimezoneInput(event.target.value)} />
                  )}
                </FormField>
                <Button type="submit" disabled={saving === "timezone"}>Saat Dilimini Kaydet</Button>
              </form>
              <form className={styles.form} onSubmit={handleSaveTimeout}>
                <FormField label="Kasa kabul bekleme süresi (dakika)">
                  {(controlProps) => (
                    <Input {...controlProps} type="number" min={1} step={1} value={timeoutMinutesInput} onChange={(event) => setTimeoutMinutesInput(event.target.value)} />
                  )}
                </FormField>
                <Button type="submit" disabled={saving === "timeout"}>Bekleme Süresini Kaydet</Button>
              </form>
            </section>

            <section className={styles.section}>
              <h2 className={styles.sectionTitle}>Çalışma Saatleri</h2>
              <Table>
                <thead><tr><th>Gün</th><th>Kapalı</th><th>Açılış</th><th>Kapanış</th></tr></thead>
                <tbody>
                  {hours.map((entry) => (
                    <tr key={entry.dayOfWeek}>
                      <td className={tableStyles.primary}>{DAY_LABELS[entry.dayOfWeek]}</td>
                      <td><input type="checkbox" checked={entry.closed} onChange={(event) => updateDay(entry.dayOfWeek, { closed: event.target.checked })} /></td>
                      <td><input type="time" className={styles.input} disabled={entry.closed} value={entry.openingTime?.slice(0, 5) ?? ""} onChange={(event) => updateDay(entry.dayOfWeek, { openingTime: event.target.value || null })} /></td>
                      <td><input type="time" className={styles.input} disabled={entry.closed} value={entry.closingTime?.slice(0, 5) ?? ""} onChange={(event) => updateDay(entry.dayOfWeek, { closingTime: event.target.value || null })} /></td>
                    </tr>
                  ))}
                </tbody>
              </Table>
              <Button disabled={saving === "hours"} onClick={handleSaveHours}>Çalışma Saatlerini Kaydet</Button>
            </section>
          </>
        ) : null}
      </main>
    </AppShell>
  );
}
