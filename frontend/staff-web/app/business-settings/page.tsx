"use client";

import { useEffect, useState } from "react";
import {
  createBusinessContact,
  getBusiness,
  listBusinessContacts,
  updateBusinessContact,
  updateBusinessSettings,
  type Business,
  type BusinessContact,
  type BusinessContactInput,
} from "@/lib/api";
import StaffNav from "@/components/layout/StaffNav";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import styles from "@/styles/admin.module.css";

const EMPTY_CONTACT_INPUT: BusinessContactInput = {
  name: "",
  phone: "",
  email: "",
  whatsappEnabled: false,
  dailyReportRecipient: false,
  monthlyReportRecipient: false,
};

/**
 * Section 4, staff-web admin screen: Business-level ayarlar (Permission.
 * BUSINESS_SETTINGS_MANAGE) - Section 12.1 defaultCurrency/defaultTimeZone fallback +
 * Section 12.3 BusinessContact (rapor alıcıları) yönetimi. Şube bazlı adres/saat
 * dilimi/çalışma saatleri zaten /branches/[branchId]'de.
 */
export default function BusinessSettingsPage() {
  const [business, setBusiness] = useState<Business | null>(null);
  const [currencyInput, setCurrencyInput] = useState("");
  const [timeZoneInput, setTimeZoneInput] = useState("");
  const [savingSettings, setSavingSettings] = useState(false);
  const [settingsError, setSettingsError] = useState<string | null>(null);
  const [settingsSuccess, setSettingsSuccess] = useState<string | null>(null);

  const [contacts, setContacts] = useState<BusinessContact[]>([]);
  const [loadingContacts, setLoadingContacts] = useState(true);
  const [contactsError, setContactsError] = useState<string | null>(null);
  const [newContact, setNewContact] = useState<BusinessContactInput>(EMPTY_CONTACT_INPUT);
  const [creatingContact, setCreatingContact] = useState(false);
  const [busyContactId, setBusyContactId] = useState<string | null>(null);

  async function reloadContacts() {
    try {
      setContacts(await listBusinessContacts());
    } catch {
      setContactsError("Kişiler yüklenemedi.");
    } finally {
      setLoadingContacts(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    async function fetchAll() {
      try {
        const [businessResult, contactsResult] = await Promise.all([getBusiness(), listBusinessContacts()]);
        if (cancelled) {
          return;
        }
        setBusiness(businessResult);
        setCurrencyInput(businessResult.defaultCurrency);
        setTimeZoneInput(businessResult.defaultTimeZone);
        setContacts(contactsResult);
      } catch {
        if (!cancelled) {
          setSettingsError("İşletme ayarları yüklenemedi.");
        }
      } finally {
        if (!cancelled) {
          setLoadingContacts(false);
        }
      }
    }
    void fetchAll();
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleSaveSettings(event: React.FormEvent) {
    event.preventDefault();
    setSavingSettings(true);
    setSettingsError(null);
    setSettingsSuccess(null);
    try {
      const updated = await updateBusinessSettings(currencyInput.trim(), timeZoneInput.trim());
      setBusiness(updated);
      setSettingsSuccess("Ayarlar kaydedildi.");
    } catch {
      setSettingsError("Ayarlar kaydedilemedi. Geçerli bir ISO 4217 para birimi kodu (ör. TRY) ve IANA saat dilimi kimliği (ör. Europe/Istanbul) girin.");
    } finally {
      setSavingSettings(false);
    }
  }

  async function handleCreateContact(event: React.FormEvent) {
    event.preventDefault();
    if (!newContact.name.trim()) {
      return;
    }
    setCreatingContact(true);
    setContactsError(null);
    try {
      await createBusinessContact({ ...newContact, name: newContact.name.trim() });
      setNewContact(EMPTY_CONTACT_INPUT);
      await reloadContacts();
    } catch {
      setContactsError("Kişi oluşturulamadı.");
    } finally {
      setCreatingContact(false);
    }
  }

  async function handleToggleActive(contact: BusinessContact) {
    setBusyContactId(contact.id);
    setContactsError(null);
    try {
      await updateBusinessContact(contact.id, {
        name: contact.name,
        phone: contact.phone ?? "",
        email: contact.email ?? "",
        whatsappEnabled: contact.whatsappEnabled,
        dailyReportRecipient: contact.dailyReportRecipient,
        monthlyReportRecipient: contact.monthlyReportRecipient,
        active: !contact.active,
      });
      await reloadContacts();
    } catch {
      setContactsError("Kişi güncellenemedi.");
    } finally {
      setBusyContactId(null);
    }
  }

  return (
    <>
      <StaffNav />
      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>İşletme Ayarları</h1>
        </div>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Varsayılan Para Birimi &amp; Saat Dilimi</h2>
          {settingsError ? <p className={styles.error}>{settingsError}</p> : null}
          {settingsSuccess ? <p className={styles.success}>{settingsSuccess}</p> : null}
          <form className={styles.form} onSubmit={handleSaveSettings}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="business-currency">
                Para Birimi (ISO 4217)
              </label>
              <input
                id="business-currency"
                className={styles.input}
                placeholder="TRY"
                value={currencyInput}
                onChange={(event) => setCurrencyInput(event.target.value.toUpperCase())}
                required
              />
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="business-timezone">
                Saat Dilimi (IANA)
              </label>
              <input
                id="business-timezone"
                className={styles.input}
                placeholder="Europe/Istanbul"
                value={timeZoneInput}
                onChange={(event) => setTimeZoneInput(event.target.value)}
                required
              />
            </div>
            <Button type="submit" disabled={savingSettings}>
              {savingSettings ? "Kaydediliyor…" : "Kaydet"}
            </Button>
          </form>
          {business ? (
            <p className={styles.rowMeta}>
              {business.name} · şu an {business.defaultCurrency} / {business.defaultTimeZone}
            </p>
          ) : null}
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Rapor Alıcıları (İşletme Sahipleri/Kişiler)</h2>

          <form className={styles.form} onSubmit={handleCreateContact}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="contact-name">
                Ad
              </label>
              <input
                id="contact-name"
                className={styles.input}
                value={newContact.name}
                onChange={(event) => setNewContact((current) => ({ ...current, name: event.target.value }))}
                required
              />
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="contact-phone">
                Telefon
              </label>
              <input
                id="contact-phone"
                className={styles.input}
                value={newContact.phone}
                onChange={(event) => setNewContact((current) => ({ ...current, phone: event.target.value }))}
              />
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="contact-email">
                E-posta
              </label>
              <input
                id="contact-email"
                type="email"
                className={styles.input}
                value={newContact.email}
                onChange={(event) => setNewContact((current) => ({ ...current, email: event.target.value }))}
              />
            </div>
            <Button type="submit" disabled={creatingContact}>
              {creatingContact ? "Ekleniyor…" : "Kişi Ekle"}
            </Button>
          </form>

          <div className={styles.rowActions}>
            <label style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <input
                type="checkbox"
                checked={newContact.dailyReportRecipient}
                onChange={(event) => setNewContact((current) => ({ ...current, dailyReportRecipient: event.target.checked }))}
              />
              Günlük rapor alsın
            </label>
            <label style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <input
                type="checkbox"
                checked={newContact.monthlyReportRecipient}
                onChange={(event) => setNewContact((current) => ({ ...current, monthlyReportRecipient: event.target.checked }))}
              />
              Aylık rapor alsın
            </label>
            <label style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <input
                type="checkbox"
                checked={newContact.whatsappEnabled}
                onChange={(event) => setNewContact((current) => ({ ...current, whatsappEnabled: event.target.checked }))}
              />
              WhatsApp bildirimleri
            </label>
          </div>

          {contactsError ? <p className={styles.error}>{contactsError}</p> : null}

          <div className={styles.list}>
            {loadingContacts ? (
              <p className={styles.empty}>Yükleniyor…</p>
            ) : contacts.length === 0 ? (
              <p className={styles.empty}>Henüz kişi yok.</p>
            ) : (
              contacts.map((contact) => (
                <div key={contact.id} className={styles.row}>
                  <div className={styles.rowMain}>
                    <span className={styles.rowTitle}>{contact.name}</span>
                    <span className={styles.rowMeta}>
                      {[contact.phone, contact.email].filter(Boolean).join(" · ") || "İletişim bilgisi yok"}
                      {" · "}
                      {[
                        contact.dailyReportRecipient ? "Günlük rapor" : null,
                        contact.monthlyReportRecipient ? "Aylık rapor" : null,
                        contact.whatsappEnabled ? "WhatsApp" : null,
                      ]
                        .filter(Boolean)
                        .join(", ") || "Rapor ayarı yok"}
                    </span>
                  </div>
                  <div className={styles.rowActions}>
                    <Badge tone={contact.active ? "neutral" : "danger"}>{contact.active ? "Aktif" : "Devre dışı"}</Badge>
                    <Button
                      size="md"
                      variant="ghost"
                      disabled={busyContactId === contact.id}
                      onClick={() => handleToggleActive(contact)}
                    >
                      {contact.active ? "Devre Dışı Bırak" : "Aktif Et"}
                    </Button>
                  </div>
                </div>
              ))
            )}
          </div>
        </section>
      </main>
    </>
  );
}
