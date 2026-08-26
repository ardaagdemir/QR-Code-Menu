"use client";

import { useEffect, useRef, useState } from "react";
import { ContactRound, Plus } from "lucide-react";
import {
  createBusinessContact,
  deleteBusinessContact,
  getBusiness,
  listBusinessContacts,
  updateBusinessContact,
  updateBusinessName,
  updateBusinessSettings,
  type BusinessContact,
  type BusinessContactInput,
} from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import { patchStaffContext } from "@/lib/staffContextStore";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import { useToast } from "@/components/ui/ToastProvider";
import ContactFormDialog from "./ContactFormDialog";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";
import pageStyles from "./page.module.css";

const EMPTY_CONTACT_INPUT: BusinessContactInput = {
  name: "",
  phone: "",
  email: "",
  whatsappEnabled: false,
  dailyReportRecipient: false,
  monthlyReportRecipient: false,
};

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
 * Section 4, staff-web admin screen: Business-level ayarlar (Permission.
 * BUSINESS_SETTINGS_MANAGE) - Section 12.1 defaultCurrency/defaultTimeZone fallback +
 * Section 12.3 BusinessContact (rapor alıcıları) yönetimi. Şube bazlı adres/saat
 * dilimi/çalışma saatleri zaten /branches/[branchId]'de.
 */
type ContactFormState =
  | { mode: "create"; value: BusinessContactInput }
  | { mode: "edit"; contactId: string; value: BusinessContactInput };

export default function BusinessSettingsPage() {
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

  const [contacts, setContacts] = useState<BusinessContact[]>([]);
  const [loadingContacts, setLoadingContacts] = useState(true);
  const [contactsError, setContactsError] = useState<string | null>(null);
  const [contactForm, setContactForm] = useState<ContactFormState | null>(null);
  const [savingContact, setSavingContact] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<BusinessContact | null>(null);
  const [deletingContactId, setDeletingContactId] = useState<string | null>(null);

  function loadContacts() {
    listBusinessContacts()
      .then((data) => {
        setContacts(data);
        setContactsError(null);
      })
      .catch(() => setContactsError("Kişiler yüklenemedi."))
      .finally(() => setLoadingContacts(false));
  }

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
    loadContacts();
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

  function openCreateContact() {
    setContactForm({ mode: "create", value: EMPTY_CONTACT_INPUT });
  }

  function openEditContact(contact: BusinessContact) {
    setContactForm({
      mode: "edit",
      contactId: contact.id,
      value: {
        name: contact.name,
        phone: contact.phone ?? "",
        email: contact.email ?? "",
        whatsappEnabled: contact.whatsappEnabled,
        dailyReportRecipient: contact.dailyReportRecipient,
        monthlyReportRecipient: contact.monthlyReportRecipient,
      },
    });
  }

  async function handleSubmitContact(event: React.FormEvent) {
    event.preventDefault();
    if (!contactForm || !contactForm.value.name.trim()) {
      return;
    }
    setSavingContact(true);
    try {
      const value = { ...contactForm.value, name: contactForm.value.name.trim() };
      if (contactForm.mode === "create") {
        await createBusinessContact(value);
        showToast("Kişi eklendi.", "success");
      } else {
        await updateBusinessContact(contactForm.contactId, value);
        showToast("Kişi güncellendi.", "success");
      }
      setContactForm(null);
      loadContacts();
    } catch {
      showToast(contactForm.mode === "create" ? "Kişi oluşturulamadı." : "Kişi güncellenemedi.", "error");
    } finally {
      setSavingContact(false);
    }
  }

  async function handleConfirmDeleteContact() {
    if (!deleteTarget) {
      return;
    }
    setDeletingContactId(deleteTarget.id);
    try {
      await deleteBusinessContact(deleteTarget.id);
      setDeleteTarget(null);
      loadContacts();
      showToast("Kişi silindi.", "success");
    } catch {
      showToast("Kişi silinemedi.", "error");
    } finally {
      setDeletingContactId(null);
    }
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader title="İşletme Ayarları" description="İşletme varsayılanlarını ve rapor alıcılarını yönetin." />

        <section className={`${styles.section} ${styles.panel} ${pageStyles.settingsPanel}`}>
          <h2 className={styles.sectionTitle}>Varsayılan Para Birimi &amp; Saat Dilimi</h2>
          {settingsError ? <ErrorState message={settingsError} /> : null}
          <form className={`${styles.form} ${pageStyles.settingsForm}`} onSubmit={handleSaveSettings}>
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
            <FormField label="Saat Dilimi" hint="Yazarak arayın veya listeden geçerli bir IANA değeri seçin." required>
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
            <Button type="submit" disabled={savingSettings}>
              {savingSettings ? "Kaydediliyor…" : "Ayarları Kaydet"}
            </Button>
          </form>
        </section>

        <section className={`${styles.section} ${styles.panel} ${pageStyles.settingsPanel}`}>
          <h2 className={styles.sectionTitle}>İşletme Adı</h2>
          {nameError ? <ErrorState message={nameError} /> : null}
          <form className={`${styles.form} ${pageStyles.settingsForm}`} onSubmit={handleSaveName}>
            <FormField label="İşletme Adı" required>
              {(controlProps) => (
                <Input {...controlProps} value={nameInput} onChange={(event) => setNameInput(event.target.value)} required />
              )}
            </FormField>
            <Button type="submit" disabled={savingName}>
              {savingName ? "Kaydediliyor…" : "Adı Kaydet"}
            </Button>
          </form>
        </section>

        <section className={`${styles.section} ${styles.panel} ${pageStyles.contactsPanel}`}>
          <PageHeader
            title="Rapor Alıcıları"
            description="Günlük ve aylık rapor tercihlerini kişi bazında yönetin."
            actions={
              <Button onClick={openCreateContact}>
                <Plus size={16} aria-hidden="true" /> Kişi Ekle
              </Button>
            }
          />

          {loadingContacts ? (
            <TableSkeleton />
          ) : contactsError ? (
            <ErrorState message={contactsError} onRetry={loadContacts} />
          ) : contacts.length === 0 ? (
            <div className={pageStyles.compactEmpty}>
              <EmptyState icon={<ContactRound size={20} />} title="Henüz kişi yok" description="Rapor alacak ilk kişiyi ekleyin." />
            </div>
          ) : (
            <Table>
              <thead>
                <tr>
                  <th>Ad</th>
                  <th>İletişim</th>
                  <th>Rapor tercihleri</th>
                  <th className={pageStyles.actionsHeader}>İşlemler</th>
                </tr>
              </thead>
              <tbody>
                {contacts.map((contact) => (
                  <tr key={contact.id}>
                    <td className={tableStyles.primary}>{contact.name}</td>
                    <td className={tableStyles.muted}>{contact.email || "İletişim bilgisi yok"}</td>
                    <td>
                      <div className={pageStyles.reportPreferences}>
                        {contact.dailyReportRecipient ? <Badge tone="info">Günlük rapor</Badge> : null}
                        {contact.monthlyReportRecipient ? <Badge tone="neutral">Aylık rapor</Badge> : null}
                        {!contact.dailyReportRecipient && !contact.monthlyReportRecipient ? (
                          <span className={pageStyles.noPreference}>Rapor tercihi yok</span>
                        ) : null}
                      </div>
                    </td>
                    <td>
                      <div className={tableStyles.actions}>
                        <Button size="md" variant="ghost" onClick={() => openEditContact(contact)}>
                          Düzenle
                        </Button>
                        <Button
                          size="md"
                          variant="danger"
                          disabled={deletingContactId === contact.id}
                          onClick={() => setDeleteTarget(contact)}
                        >
                          Sil
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </section>
      </main>

      {contactForm ? (
        <ContactFormDialog
          mode={contactForm.mode}
          value={contactForm.value}
          onChange={(value) => setContactForm((current) => (current ? { ...current, value } : current))}
          onSubmit={handleSubmitContact}
          onClose={() => setContactForm(null)}
          submitting={savingContact}
        />
      ) : null}

      {deleteTarget ? (
        <ConfirmDialog
          title="Kişiyi sil"
          message={`"${deleteTarget.name}" adlı kişiyi silmek istediğinize emin misiniz? Bu işlem geri alınamaz.`}
          confirmLabel="Sil"
          tone="danger"
          confirmLoading={deletingContactId === deleteTarget.id}
          onConfirm={handleConfirmDeleteContact}
          onCancel={() => setDeleteTarget(null)}
        />
      ) : null}
    </AppShell>
  );
}
