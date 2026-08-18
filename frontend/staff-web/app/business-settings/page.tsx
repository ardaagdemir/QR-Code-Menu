"use client";

import { useEffect, useId, useState } from "react";
import { ContactRound, Plus } from "lucide-react";
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
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Dialog from "@/components/ui/Dialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import { useToast } from "@/components/ui/ToastProvider";
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

/**
 * Section 4, staff-web admin screen: Business-level ayarlar (Permission.
 * BUSINESS_SETTINGS_MANAGE) - Section 12.1 defaultCurrency/defaultTimeZone fallback +
 * Section 12.3 BusinessContact (rapor alıcıları) yönetimi. Şube bazlı adres/saat
 * dilimi/çalışma saatleri zaten /branches/[branchId]'de.
 */
export default function BusinessSettingsPage() {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [business, setBusiness] = useState<Business | null>(null);
  const [currencyInput, setCurrencyInput] = useState("");
  const [timeZoneInput, setTimeZoneInput] = useState("");
  const [savingSettings, setSavingSettings] = useState(false);
  const [settingsError, setSettingsError] = useState<string | null>(null);

  const [contacts, setContacts] = useState<BusinessContact[]>([]);
  const [loadingContacts, setLoadingContacts] = useState(true);
  const [contactsError, setContactsError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [newContact, setNewContact] = useState<BusinessContactInput>(EMPTY_CONTACT_INPUT);
  const [creatingContact, setCreatingContact] = useState(false);
  const [busyContactId, setBusyContactId] = useState<string | null>(null);

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
        setBusiness(businessResult);
        setCurrencyInput(businessResult.defaultCurrency);
        setTimeZoneInput(businessResult.defaultTimeZone);
      } catch {
        setSettingsError("İşletme ayarları yüklenemedi.");
      }
    }
    void fetchAll();
    loadContacts();
  }, []);

  async function handleSaveSettings(event: React.FormEvent) {
    event.preventDefault();
    setSavingSettings(true);
    try {
      const updated = await updateBusinessSettings(currencyInput.trim(), timeZoneInput.trim());
      setBusiness(updated);
      setSettingsError(null);
      showToast("Ayarlar kaydedildi.", "success");
    } catch {
      showToast("Ayarlar kaydedilemedi. Geçerli bir ISO 4217 para birimi kodu (ör. TRY) ve IANA saat dilimi kimliği (ör. Europe/Istanbul) girin.", "error");
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
    try {
      await createBusinessContact({ ...newContact, name: newContact.name.trim() });
      setNewContact(EMPTY_CONTACT_INPUT);
      setCreateOpen(false);
      loadContacts();
      showToast("Kişi eklendi.", "success");
    } catch {
      showToast("Kişi oluşturulamadı.", "error");
    } finally {
      setCreatingContact(false);
    }
  }

  async function handleToggleActive(contact: BusinessContact) {
    setBusyContactId(contact.id);
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
      loadContacts();
    } catch {
      showToast("Kişi güncellenemedi.", "error");
    } finally {
      setBusyContactId(null);
    }
  }

  function reportTags(contact: BusinessContact): string {
    return (
      [
        contact.dailyReportRecipient ? "Günlük rapor" : null,
        contact.monthlyReportRecipient ? "Aylık rapor" : null,
        contact.whatsappEnabled ? "WhatsApp" : null,
      ]
        .filter(Boolean)
        .join(", ") || "Rapor ayarı yok"
    );
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader title="Ayarlar" description="İşletme varsayılanlarını ve rapor bildirimlerini alacak kişileri yönetin." />

        <section className={`${styles.section} ${styles.panel} ${pageStyles.settingsPanel}`}>
          <h2 className={styles.sectionTitle}>Varsayılan Para Birimi &amp; Saat Dilimi</h2>
          {settingsError ? <ErrorState message={settingsError} /> : null}
          <form className={`${styles.form} ${pageStyles.settingsForm}`} onSubmit={handleSaveSettings}>
            <FormField label="Para Birimi (ISO 4217)" required>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  placeholder="TRY"
                  value={currencyInput}
                  onChange={(event) => setCurrencyInput(event.target.value.toUpperCase())}
                  required
                />
              )}
            </FormField>
            <FormField label="Saat Dilimi (IANA)" required>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  placeholder="Europe/Istanbul"
                  value={timeZoneInput}
                  onChange={(event) => setTimeZoneInput(event.target.value)}
                  required
                />
              )}
            </FormField>
            <Button type="submit" disabled={savingSettings}>
              {savingSettings ? "Kaydediliyor…" : "Kaydet"}
            </Button>
          </form>
          {business ? (
            <p className={`${styles.rowMeta} ${pageStyles.currentSetting}`}>
              {business.name} · şu an {business.defaultCurrency} / {business.defaultTimeZone}
            </p>
          ) : null}
        </section>

        <section className={`${styles.section} ${styles.panel} ${pageStyles.contactsPanel}`}>
          <PageHeader
            title="Rapor Alıcıları"
            description="İşletme sahipleri/kişiler - günlük/aylık rapor ve WhatsApp bildirimi alır."
            actions={
              <Button onClick={() => setCreateOpen(true)}>
                <Plus size={16} aria-hidden="true" /> Kişi Ekle
              </Button>
            }
          />

          {loadingContacts ? (
            <TableSkeleton />
          ) : contactsError ? (
            <ErrorState message={contactsError} onRetry={loadContacts} />
          ) : contacts.length === 0 ? (
            <EmptyState icon={<ContactRound size={20} />} title="Henüz kişi yok" description="Rapor alacak ilk kişiyi ekleyin." />
          ) : (
            <Table>
              <thead>
                <tr>
                  <th>Ad</th>
                  <th>İletişim</th>
                  <th>Rapor tercihleri</th>
                  <th>Durum</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {contacts.map((contact) => (
                  <tr key={contact.id}>
                    <td className={tableStyles.primary}>{contact.name}</td>
                    <td className={tableStyles.muted}>{[contact.phone, contact.email].filter(Boolean).join(" · ") || "İletişim bilgisi yok"}</td>
                    <td className={tableStyles.muted}>{reportTags(contact)}</td>
                    <td>
                      <Badge tone={contact.active ? "success" : "danger"}>{contact.active ? "Aktif" : "Devre dışı"}</Badge>
                    </td>
                    <td>
                      <div className={tableStyles.actions}>
                        <Button className={contact.active ? pageStyles.dangerAction : undefined} size="md" variant={contact.active ? "ghost" : "secondary"} disabled={busyContactId === contact.id} onClick={() => handleToggleActive(contact)}>
                          {contact.active ? "Devre Dışı Bırak" : "Aktif Et"}
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

      {createOpen ? (
        <Dialog onClose={() => setCreateOpen(false)} labelledBy={dialogTitleId}>
          <h2 id={dialogTitleId} className={styles.sectionTitle}>
            Yeni Kişi
          </h2>
          <form className={styles.section} onSubmit={handleCreateContact}>
            <FormField label="Ad" required>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  value={newContact.name}
                  onChange={(event) => setNewContact((current) => ({ ...current, name: event.target.value }))}
                  required
                />
              )}
            </FormField>
            <FormField label="Telefon">
              {(controlProps) => (
                <Input
                  {...controlProps}
                  value={newContact.phone}
                  onChange={(event) => setNewContact((current) => ({ ...current, phone: event.target.value }))}
                />
              )}
            </FormField>
            <FormField label="E-posta">
              {(controlProps) => (
                <Input
                  {...controlProps}
                  type="email"
                  value={newContact.email}
                  onChange={(event) => setNewContact((current) => ({ ...current, email: event.target.value }))}
                />
              )}
            </FormField>

            <div className={styles.rowActions}>
              <label className={styles.checkboxLabel}>
                <input
                  type="checkbox"
                  checked={newContact.dailyReportRecipient}
                  onChange={(event) => setNewContact((current) => ({ ...current, dailyReportRecipient: event.target.checked }))}
                />
                Günlük rapor alsın
              </label>
              <label className={styles.checkboxLabel}>
                <input
                  type="checkbox"
                  checked={newContact.monthlyReportRecipient}
                  onChange={(event) => setNewContact((current) => ({ ...current, monthlyReportRecipient: event.target.checked }))}
                />
                Aylık rapor alsın
              </label>
              <label className={styles.checkboxLabel}>
                <input
                  type="checkbox"
                  checked={newContact.whatsappEnabled}
                  onChange={(event) => setNewContact((current) => ({ ...current, whatsappEnabled: event.target.checked }))}
                />
                WhatsApp bildirimleri
              </label>
            </div>

            <Button type="submit" disabled={creatingContact}>
              {creatingContact ? "Ekleniyor…" : "Kişi Ekle"}
            </Button>
          </form>
        </Dialog>
      ) : null}
    </AppShell>
  );
}
