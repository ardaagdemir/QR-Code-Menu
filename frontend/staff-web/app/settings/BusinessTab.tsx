"use client";

import { useEffect, useRef, useState } from "react";
import { getBusiness, updateBusinessName, updateBusinessSettings } from "@/lib/api";
import { patchStaffContext } from "@/lib/staffContextStore";
import ErrorState from "@/components/ui/ErrorState";
import Button from "@/components/ui/Button";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import { useToast } from "@/components/ui/ToastProvider";
import styles from "@/styles/admin.module.css";
import pageStyles from "./page.module.css";

const COMMON_CURRENCY_OPTIONS = [
  "TRY",
  "EUR",
  "USD",
  "GBP",
  "AED",
  "SAR",
  "AZN",
  "CHF",
  "CAD",
  "AUD",
  "JPY",
];

const COMMON_TIMEZONE_OPTIONS = [
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

function getSupportedIntlValues(key: "currency" | "timeZone", fallback: string[]): string[] {
  const supportedValuesOf = (Intl as typeof Intl & {
    supportedValuesOf?: (valueKey: "currency" | "timeZone") => string[];
  }).supportedValuesOf;

  if (!supportedValuesOf) return fallback;

  try {
    return supportedValuesOf(key);
  } catch {
    return fallback;
  }
}

/**
 * Ayarlar ekranının "İşletme" sekmesi (Permission.BUSINESS_SETTINGS_MANAGE) - Section 12.1
 * defaultCurrency/defaultTimeZone fallback. Şube bazlı adres/saat dilimi/çalışma saatleri
 * "Şube" sekmesinde (BranchTab); rapor alıcıları (Section 12.3 BusinessContact) Raporlar
 * sayfasına taşındı - REPORT_VIEW rolleri (BRANCH_MANAGER/CASHIER) BUSINESS_SETTINGS_MANAGE
 * gerektiren bu sekmeye hiç erişemediği için, yönetimin kendilerinin de görebildiği Raporlar
 * sayfasında olması gerekiyordu.
 */
export default function BusinessTab() {
  const { showToast } = useToast();

  const [nameInput, setNameInput] = useState("");
  const [savingName, setSavingName] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);
  const savingNameRef = useRef(false);

  const [currencyInput, setCurrencyInput] = useState("");
  const [timeZoneInput, setTimeZoneInput] = useState("");
  const [currencyOptions, setCurrencyOptions] = useState<string[]>(COMMON_CURRENCY_OPTIONS);
  const [timeZoneOptions, setTimeZoneOptions] = useState<string[]>(COMMON_TIMEZONE_OPTIONS);
  const [savingSettings, setSavingSettings] = useState(false);
  const [settingsError, setSettingsError] = useState<string | null>(null);
  // `disabled={savingSettings}` only takes effect after React commits the next render, so a
  // second submit event that lands before that repaint (fast double-click, or Enter + click)
  // still reaches this handler with savingSettings still false in its closure and fires a
  // second POST - one of the two then 400s on the backend's own duplicate-update handling.
  // A ref is checked/set synchronously, closing that gap regardless of render timing.
  const savingSettingsRef = useRef(false);

  useEffect(() => {
    async function fetchAll() {
      try {
        const businessResult = await getBusiness();
        setNameInput(businessResult.name);
        setCurrencyInput(businessResult.defaultCurrency);
        setTimeZoneInput(businessResult.defaultTimeZone);
      } catch {
        setSettingsError("İşletme ayarları yüklenemedi.");
      }
    }
    void fetchAll();
  }, []);

  async function handleSaveName(event: React.FormEvent) {
    event.preventDefault();
    if (savingNameRef.current) {
      return;
    }
    const trimmed = nameInput.trim();
    if (!trimmed) {
      setNameError("İşletme adı boş olamaz.");
      return;
    }
    savingNameRef.current = true;
    setSavingName(true);
    try {
      const updated = await updateBusinessName(trimmed);
      setNameInput(updated.name);
      patchStaffContext({ businessName: updated.name });
      setNameError(null);
      showToast("İşletme adı güncellendi.", "success");
    } catch {
      showToast("İşletme adı güncellenemedi.", "error");
    } finally {
      savingNameRef.current = false;
      setSavingName(false);
    }
  }

  async function handleSaveSettings(event: React.FormEvent) {
    event.preventDefault();
    if (savingSettingsRef.current) {
      return;
    }
    savingSettingsRef.current = true;
    setSavingSettings(true);
    try {
      const updated = await updateBusinessSettings(currencyInput.trim(), timeZoneInput.trim());
      setCurrencyInput(updated.defaultCurrency);
      setTimeZoneInput(updated.defaultTimeZone);
      setSettingsError(null);
      showToast("Ayarlar kaydedildi.", "success");
    } catch {
      showToast("Ayarlar kaydedilemedi. Geçerli bir ISO 4217 para birimi kodu (ör. TRY) ve IANA saat dilimi kimliği (ör. Europe/Istanbul) girin.", "error");
    } finally {
      savingSettingsRef.current = false;
      setSavingSettings(false);
    }
  }

  return (
    <div className={pageStyles.topRow}>
      <section className={`${styles.section} ${styles.panel} ${pageStyles.settingsPanel}`}>
        <h2 className={styles.sectionTitle}>Varsayılan Para Birimi &amp; Saat Dilimi</h2>
        {settingsError ? <ErrorState message={settingsError} /> : null}
        <form className={pageStyles.currencyForm} onSubmit={handleSaveSettings}>
          <FormField label="Para Birimi" hint="ISO 4217" required>
            {(controlProps) => (
              <Select
                {...controlProps}
                value={currencyInput}
                onFocus={() => setCurrencyOptions(getSupportedIntlValues("currency", COMMON_CURRENCY_OPTIONS))}
                onChange={(event) => setCurrencyInput(event.target.value)}
                required
              >
                {currencyInput && !currencyOptions.includes(currencyInput) ? <option value={currencyInput}>{currencyInput}</option> : null}
                {currencyOptions.map((currency) => <option key={currency} value={currency}>{currency}</option>)}
              </Select>
            )}
          </FormField>
          <FormField label="Saat Dilimi" hint="Yazarak arayın veya listeden geçerli bir IANA değeri seç." required>
            {(controlProps) => (
              <>
                <Input
                  {...controlProps}
                  list={`${controlProps.id}-timezones`}
                  autoComplete="off"
                  placeholder="Europe/Istanbul"
                  value={timeZoneInput}
                  onFocus={() => setTimeZoneOptions(getSupportedIntlValues("timeZone", COMMON_TIMEZONE_OPTIONS))}
                  onChange={(event) => setTimeZoneInput(event.target.value)}
                  required
                />
                <datalist id={`${controlProps.id}-timezones`}>
                  {timeZoneOptions.map((timeZone) => <option key={timeZone} value={timeZone} />)}
                </datalist>
              </>
            )}
          </FormField>
          <Button type="submit" size="sm" disabled={savingSettings}>
            {savingSettings ? "Kaydediliyor…" : "Ayarları Kaydet"}
          </Button>
        </form>
      </section>

      <section className={`${styles.section} ${styles.panel} ${pageStyles.settingsPanel}`}>
        <h2 className={styles.sectionTitle}>İşletme Adı</h2>
        {nameError ? <ErrorState message={nameError} /> : null}
        <form className={pageStyles.nameForm} onSubmit={handleSaveName}>
          <FormField label="İşletme Adı" required>
            {(controlProps) => (
              <Input {...controlProps} value={nameInput} onChange={(event) => setNameInput(event.target.value)} required />
            )}
          </FormField>
          <Button type="submit" size="sm" disabled={savingName}>
            {savingName ? "Kaydediliyor…" : "Adı Kaydet"}
          </Button>
        </form>
      </section>
    </div>
  );
}
