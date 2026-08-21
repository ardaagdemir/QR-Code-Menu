"use client";

import { useEffect, useState } from "react";
import { Clock3, MapPin, Settings2 } from "lucide-react";
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
import pageStyles from "./page.module.css";

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

const IANA_TIMEZONE_OPTIONS = [
  "UTC",
  "Europe/Istanbul",
  "Europe/Athens",
  "Europe/Berlin",
  "Europe/Brussels",
  "Europe/Bucharest",
  "Europe/Helsinki",
  "Europe/London",
  "Europe/Madrid",
  "Europe/Moscow",
  "Europe/Paris",
  "Europe/Rome",
  "Europe/Vienna",
  "Europe/Warsaw",
  "Asia/Baku",
  "Asia/Dubai",
  "Asia/Jerusalem",
  "Asia/Qatar",
  "Asia/Riyadh",
  "Asia/Tbilisi",
  "Asia/Tehran",
  "America/New_York",
  "America/Chicago",
  "America/Denver",
  "America/Los_Angeles",
];

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

  async function handleSaveBranchInfo(event: React.FormEvent) {
    event.preventDefault();
    const minutes = Number(timeoutMinutesInput);
    if (!Number.isFinite(minutes) || minutes <= 0) {
      showToast("Geçerli bir dakika değeri girin.", "error");
      return;
    }
    setSaving("info");
    try {
      await setAddress(addressInput.trim());
      await setBranchTimezone(timezoneInput.trim() || null);
      setBranch(await setStoreAcceptanceTimeout(Math.round(minutes * 60)));
      showToast("Şube bilgileri kaydedildi.", "success");
    } catch {
      showToast("Şube bilgileri kaydedilemedi. Adres, saat dilimi ve kabul süresini kontrol edin.", "error");
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
      <main className={`${styles.page} ${pageStyles.page}`}>
        <PageHeader title="Şube Ayarları" description={branch?.name ?? "Aktif şube ayarları"} />

        {loading ? <TableSkeleton /> : error ? <ErrorState message={error} /> : null}

        {!loading && !error && branch ? (
          <>
            <section className={`${styles.section} ${styles.panel} ${pageStyles.card}`}>
              <div className={pageStyles.cardHeader}>
                <span className={pageStyles.cardIcon} aria-hidden="true"><Settings2 size={19} /></span>
                <div>
                  <h2 className={styles.sectionTitle}>Operasyon</h2>
                  <p className={pageStyles.cardDescription}>Sipariş kabul durumunu ve servis akışını yönetin.</p>
                </div>
              </div>
              <form className={pageStyles.sectionForm} onSubmit={handleSaveDeliveryModel}>
                <div className={pageStyles.operationGrid}>
                  <div className={pageStyles.togglePanel}>
                    <span className={pageStyles.toggleLabel}>
                      Sipariş alımı
                      <span className={pageStyles.toggleHint}>Şu anda {branch.orderingEnabled ? "açık" : "kapalı"}</span>
                    </span>
                    <label className={pageStyles.switch}>
                      <input
                        type="checkbox"
                        role="switch"
                        aria-label="Sipariş alımını aç veya kapat"
                        checked={branch.orderingEnabled}
                        disabled={saving !== null}
                        onChange={handleToggleOrdering}
                      />
                      <span className={pageStyles.switchTrack} aria-hidden="true" />
                    </label>
                  </div>
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
                </div>
                <div className={pageStyles.cardFooter}>
                  <Button type="submit" disabled={saving !== null}>
                    {saving === "delivery" ? "Kaydediliyor…" : "Operasyonu Kaydet"}
                  </Button>
                </div>
              </form>
            </section>

            <section className={`${styles.section} ${styles.panel} ${pageStyles.card}`}>
              <div className={pageStyles.cardHeader}>
                <span className={pageStyles.cardIcon} aria-hidden="true"><MapPin size={19} /></span>
                <div>
                  <h2 className={styles.sectionTitle}>Şube Bilgileri</h2>
                  <p className={pageStyles.cardDescription}>Konum ve şubeye özel operasyon varsayılanları.</p>
                </div>
              </div>
              <form className={pageStyles.sectionForm} onSubmit={handleSaveBranchInfo}>
                <div className={pageStyles.infoGrid}>
                  <FormField label="Adres">
                    {(controlProps) => <Input {...controlProps} value={addressInput} onChange={(event) => setAddressInput(event.target.value)} />}
                  </FormField>
                  <FormField label="Saat dilimi" hint="Yazarak arayın veya listeden seçin. Boşsa işletme varsayılanı kullanılır.">
                    {(controlProps) => (
                      <>
                        <Input
                          {...controlProps}
                          list={`${controlProps.id}-timezones`}
                          autoComplete="off"
                          placeholder="Europe/Istanbul"
                          value={timezoneInput}
                          onChange={(event) => setTimezoneInput(event.target.value)}
                        />
                        <datalist id={`${controlProps.id}-timezones`}>
                          {IANA_TIMEZONE_OPTIONS.map((timezone) => <option key={timezone} value={timezone} />)}
                        </datalist>
                      </>
                    )}
                  </FormField>
                  <FormField label="Kasa kabul süresi">
                    {(controlProps) => (
                      <div className={pageStyles.suffixInputWrap}>
                        <Input
                          {...controlProps}
                          className={pageStyles.suffixInput}
                          type="number"
                          min={1}
                          step={1}
                          value={timeoutMinutesInput}
                          onChange={(event) => setTimeoutMinutesInput(event.target.value)}
                        />
                        <span className={pageStyles.inputSuffix} aria-hidden="true">dk</span>
                      </div>
                    )}
                  </FormField>
                </div>
                <div className={pageStyles.cardFooter}>
                  <Button type="submit" disabled={saving !== null}>
                    {saving === "info" ? "Kaydediliyor…" : "Şube Bilgilerini Kaydet"}
                  </Button>
                </div>
              </form>
            </section>

            <section className={`${styles.section} ${styles.panel} ${pageStyles.card}`}>
              <div className={pageStyles.cardHeader}>
                <span className={pageStyles.cardIcon} aria-hidden="true"><Clock3 size={19} /></span>
                <div>
                  <h2 className={styles.sectionTitle}>Çalışma Saatleri</h2>
                  <p className={pageStyles.cardDescription}>Şubenin haftalık açık ve kapalı olduğu saatler.</p>
                </div>
              </div>
              <div className={pageStyles.hoursWrap}>
                <Table>
                  <colgroup>
                    <col className={pageStyles.dayColumn} />
                    <col className={pageStyles.statusColumn} />
                    <col className={pageStyles.timeColumn} />
                    <col className={pageStyles.timeColumn} />
                  </colgroup>
                  <thead><tr><th>Gün</th><th className={pageStyles.statusCell}>Durum</th><th>Açılış</th><th>Kapanış</th></tr></thead>
                  <tbody>
                    {hours.map((entry) => (
                      <tr key={entry.dayOfWeek}>
                        <td className={tableStyles.primary}>{DAY_LABELS[entry.dayOfWeek]}</td>
                        <td className={pageStyles.statusCell}>
                          <div className={pageStyles.hoursStatusControl}>
                            <span className={entry.closed ? pageStyles.hoursStatusClosed : pageStyles.hoursStatusOpen}>
                              {entry.closed ? "Kapalı" : "Açık"}
                            </span>
                            <label className={pageStyles.switch}>
                              <input
                                type="checkbox"
                                role="switch"
                                aria-label={`${DAY_LABELS[entry.dayOfWeek]} çalışma durumu`}
                                checked={!entry.closed}
                                onChange={(event) => updateDay(entry.dayOfWeek, { closed: !event.target.checked })}
                              />
                              <span className={pageStyles.switchTrack} aria-hidden="true" />
                            </label>
                          </div>
                        </td>
                        <td><Input aria-label={`${DAY_LABELS[entry.dayOfWeek]} açılış saati`} type="time" className={pageStyles.timeInput} disabled={entry.closed} value={entry.openingTime?.slice(0, 5) ?? ""} onChange={(event) => updateDay(entry.dayOfWeek, { openingTime: event.target.value || null })} /></td>
                        <td><Input aria-label={`${DAY_LABELS[entry.dayOfWeek]} kapanış saati`} type="time" className={pageStyles.timeInput} disabled={entry.closed} value={entry.closingTime?.slice(0, 5) ?? ""} onChange={(event) => updateDay(entry.dayOfWeek, { closingTime: event.target.value || null })} /></td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              </div>
              <div className={pageStyles.cardFooter}>
                <Button disabled={saving !== null} onClick={handleSaveHours}>
                  {saving === "hours" ? "Kaydediliyor…" : "Çalışma Saatlerini Kaydet"}
                </Button>
              </div>
            </section>
          </>
        ) : null}
      </main>
    </AppShell>
  );
}
