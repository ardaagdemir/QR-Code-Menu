"use client";

import { useEffect, useState, type ReactNode } from "react";
import { Clock3, Copy, MapPin, Settings2 } from "lucide-react";
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
  type StaffContext,
} from "@/lib/api";
import Button from "@/components/ui/Button";
import ErrorState from "@/components/ui/ErrorState";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
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
const WEEKDAYS: DayOfWeek[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];

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

function getDay(hours: BranchBusinessHoursEntry[], day: DayOfWeek): BranchBusinessHoursEntry {
  return hours.find((entry) => entry.dayOfWeek === day) ?? defaultHoursForDay(day);
}

function hoursEqual(a: BranchBusinessHoursEntry, b: BranchBusinessHoursEntry): boolean {
  return a.closed === b.closed && a.openingTime === b.openingTime && a.closingTime === b.closingTime;
}

type HoursRow = {
  key: string;
  label: string;
  hint?: string;
  entry: BranchBusinessHoursEntry;
  onChange: (patch: Partial<BranchBusinessHoursEntry>) => void;
  trailing?: ReactNode;
};

type Props = {
  role: StaffContext["role"];
};

/**
 * Ayarlar ekranının "Şube" sekmesi. BRANCH_MANAGER yalnızca Permission.ORDERING_TOGGLE'a
 * sahip (backend, StaffRole.java) - bu yüzden `canManageBranch` dışındaki her şey (Şube
 * Bilgileri, teslimat modeli/kabul süresi, Çalışma Saatleri) yalnızca BUSINESS_ADMIN'e açık,
 * bu ekrandan önceki /branches sayfasındaki davranışla birebir aynı.
 *
 * Çalışma Saatleri: backend hâlâ 7 ayrı günü ayrı ayrı tutuyor (BranchBusinessHoursEntry[]) -
 * burada yalnızca UI Pzt-Cum'u, hepsi birbirine eşitse tek satırda gösteriyor. Beş gün
 * birbirinden farklıysa (weekdaysUniform=false) satır otomatik olarak 5 ayrı güne genişler,
 * hiçbir gün sessizce diğerinin değerine eşitlenmez.
 */
export default function BranchTab({ role }: Props) {
  const { showToast } = useToast();
  const [branch, setBranch] = useState<Branch | null>(null);
  const [addressInput, setAddressInput] = useState("");
  const [timezoneInput, setTimezoneInput] = useState("");
  const [timeoutMinutesInput, setTimeoutMinutesInput] = useState("5");
  const [deliveryModel, setDeliveryModelInput] = useState<DeliveryModel>("WAITER_DELIVERY");
  const [hours, setHours] = useState<BranchBusinessHoursEntry[]>(DAYS_OF_WEEK.map(defaultHoursForDay));
  const [weekdaysExpanded, setWeekdaysExpanded] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState<string | null>(null);

  const canManageBranch = role === "BUSINESS_ADMIN";

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

  async function handleSaveOperation(event: React.FormEvent) {
    event.preventDefault();
    const minutes = Number(timeoutMinutesInput);
    if (!Number.isFinite(minutes) || minutes <= 0) {
      showToast("Geçerli bir dakika değeri girin.", "error");
      return;
    }
    setSaving("operation");
    try {
      await setDeliveryModel(deliveryModel);
      setBranch(await setStoreAcceptanceTimeout(Math.round(minutes * 60)));
      showToast("Ayarlar kaydedildi.", "success");
    } catch {
      showToast("Ayarlar kaydedilemedi. Kabul süresini kontrol edin.", "error");
    } finally {
      setSaving(null);
    }
  }

  async function handleSaveBranchInfo(event: React.FormEvent) {
    event.preventDefault();
    setSaving("info");
    try {
      await setAddress(addressInput.trim());
      setBranch(await setBranchTimezone(timezoneInput.trim() || null));
      showToast("Şube bilgileri kaydedildi.", "success");
    } catch {
      showToast("Şube bilgileri kaydedilemedi. Adres ve saat dilimini kontrol edin.", "error");
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

  function updateWeekdays(patch: Partial<BranchBusinessHoursEntry>) {
    setHours((current) => current.map((entry) => (WEEKDAYS.includes(entry.dayOfWeek) ? { ...entry, ...patch } : entry)));
  }

  function applyToAllDays() {
    const monday = getDay(hours, "MONDAY");
    const patch = { openingTime: monday.openingTime, closingTime: monday.closingTime, closed: monday.closed };
    setHours(DAYS_OF_WEEK.map((day) => ({ dayOfWeek: day, ...patch })));
    setWeekdaysExpanded(false);
  }

  const weekdayEntries = WEEKDAYS.map((day) => getDay(hours, day));
  const weekdaysUniform = weekdayEntries.every((entry) => hoursEqual(entry, weekdayEntries[0]));
  const showWeekdaysExpanded = weekdaysExpanded || !weekdaysUniform;

  const weekdayToggle = showWeekdaysExpanded ? (
    weekdaysUniform ? (
      <button type="button" className={pageStyles.hoursGroupToggle} onClick={() => setWeekdaysExpanded(false)}>
        Tek satırda birleştir
      </button>
    ) : null
  ) : (
    <button type="button" className={pageStyles.hoursGroupToggle} onClick={() => setWeekdaysExpanded(true)}>
      Günlere göre düzenle
    </button>
  );

  const rows: HoursRow[] = showWeekdaysExpanded
    ? [
        ...WEEKDAYS.map((day, index) => ({
          key: day,
          label: DAY_LABELS[day],
          hint: index === 0 ? "Pzt - Cum" : undefined,
          entry: getDay(hours, day),
          onChange: (patch: Partial<BranchBusinessHoursEntry>) => updateDay(day, patch),
          trailing: index === 0 ? weekdayToggle : undefined,
        })),
        { key: "SATURDAY", label: "Cumartesi", entry: getDay(hours, "SATURDAY"), onChange: (patch: Partial<BranchBusinessHoursEntry>) => updateDay("SATURDAY", patch) },
        { key: "SUNDAY", label: "Pazar", entry: getDay(hours, "SUNDAY"), onChange: (patch: Partial<BranchBusinessHoursEntry>) => updateDay("SUNDAY", patch) },
      ]
    : [
        { key: "WEEKDAYS", label: "Hafta içi", hint: "Pzt - Cum", entry: weekdayEntries[0], onChange: updateWeekdays, trailing: weekdayToggle },
        { key: "SATURDAY", label: "Cumartesi", entry: getDay(hours, "SATURDAY"), onChange: (patch: Partial<BranchBusinessHoursEntry>) => updateDay("SATURDAY", patch) },
        { key: "SUNDAY", label: "Pazar", entry: getDay(hours, "SUNDAY"), onChange: (patch: Partial<BranchBusinessHoursEntry>) => updateDay("SUNDAY", patch) },
      ];

  return (
    <>
      {loading ? <TableSkeleton /> : error ? <ErrorState message={error} /> : null}

      {!loading && !error && branch ? (
        <>
          <section className={`${styles.section} ${styles.panel} ${pageStyles.card}`}>
            <div className={pageStyles.cardHeader}>
              <span className={pageStyles.cardIcon} aria-hidden="true"><Settings2 size={19} /></span>
              <div>
                <h2 className={styles.sectionTitle}>Şube Ayarları</h2>
                <p className={pageStyles.cardDescription}>Sipariş kabul durumunu ve servis akışını yönetin.</p>
              </div>
            </div>
            <form className={pageStyles.sectionForm} onSubmit={handleSaveOperation}>
              <div className={pageStyles.operationGrid}>
                <div className={pageStyles.togglePanel}>
                  <span className={pageStyles.toggleLabel}>
                    Sipariş kabul ediliyor
                    <span className={pageStyles.toggleHint}>Şu anda {branch.openNow ? "açık" : "kapalı"}</span>
                  </span>
                  <label className={pageStyles.switch}>
                    <input
                      type="checkbox"
                      role="switch"
                      aria-label="Sipariş kabulünü aç veya kapat"
                      checked={branch.orderingEnabled}
                      disabled={saving !== null}
                      onChange={handleToggleOrdering}
                    />
                    <span className={pageStyles.switchTrack} aria-hidden="true" />
                  </label>
                </div>
                {canManageBranch ? (
                  <FormField label="Varsayılan servis tipi">
                    {(controlProps) => (
                      <Select
                        {...controlProps}
                        value={deliveryModel}
                        onChange={(event) => setDeliveryModelInput(event.target.value as DeliveryModel)}
                      >
                        <option value="WAITER_DELIVERY">Masa Servisi</option>
                        <option value="CUSTOMER_PICKUP">Müşteri kendi alır (pickup)</option>
                      </Select>
                    )}
                  </FormField>
                ) : null}
                {canManageBranch ? (
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
                ) : null}
              </div>
              {canManageBranch ? (
                <div className={pageStyles.cardFooter}>
                  <Button type="submit" disabled={saving !== null}>
                    {saving === "operation" ? "Kaydediliyor…" : "Ayarları Kaydet"}
                  </Button>
                </div>
              ) : null}
            </form>
          </section>

          {canManageBranch ? (
            <>
              <section className={`${styles.section} ${styles.panel} ${pageStyles.card}`}>
                <div className={pageStyles.cardHeader}>
                  <span className={pageStyles.cardIcon} aria-hidden="true"><MapPin size={19} /></span>
                  <div>
                    <h2 className={styles.sectionTitle}>Şube Bilgileri</h2>
                    <p className={pageStyles.cardDescription}>Aktif şubenin temel bilgilerini yönetin.</p>
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
                    <p className={pageStyles.cardDescription}>Şubenizin çalışma saatlerini yönetin.</p>
                  </div>
                  <div className={pageStyles.cardHeaderAction}>
                    <Button type="button" variant="secondary" size="sm" onClick={applyToAllDays}>
                      <Copy size={15} aria-hidden="true" /> Tüm günlere uygula
                    </Button>
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
                      {rows.map((row) => (
                        <tr key={row.key}>
                          <td className={tableStyles.primary}>
                            <span className={pageStyles.dayLabel}>{row.label}</span>
                            {row.hint ? <span className={pageStyles.dayHint}>{row.hint}</span> : null}
                            {row.trailing ? <div className={pageStyles.dayTrailing}>{row.trailing}</div> : null}
                          </td>
                          <td className={pageStyles.statusCell}>
                            <div className={pageStyles.hoursStatusControl}>
                              <span className={row.entry.closed ? pageStyles.hoursStatusClosed : pageStyles.hoursStatusOpen}>
                                {row.entry.closed ? "Kapalı" : "Açık"}
                              </span>
                              <label className={pageStyles.switch}>
                                <input
                                  type="checkbox"
                                  role="switch"
                                  aria-label={`${row.label} çalışma durumu`}
                                  checked={!row.entry.closed}
                                  onChange={(event) => row.onChange({ closed: !event.target.checked })}
                                />
                                <span className={pageStyles.switchTrack} aria-hidden="true" />
                              </label>
                            </div>
                          </td>
                          <td><Input aria-label={`${row.label} açılış saati`} type="time" className={pageStyles.timeInput} disabled={row.entry.closed} value={row.entry.openingTime?.slice(0, 5) ?? ""} onChange={(event) => row.onChange({ openingTime: event.target.value || null })} /></td>
                          <td><Input aria-label={`${row.label} kapanış saati`} type="time" className={pageStyles.timeInput} disabled={row.entry.closed} value={row.entry.closingTime?.slice(0, 5) ?? ""} onChange={(event) => row.onChange({ closingTime: event.target.value || null })} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </Table>
                </div>
                <div className={pageStyles.cardFooter}>
                  <Button disabled={saving !== null} onClick={handleSaveHours}>
                    {saving === "hours" ? "Kaydediliyor…" : "Saatleri Kaydet"}
                  </Button>
                </div>
              </section>
            </>
          ) : null}
        </>
      ) : null}
    </>
  );
}
