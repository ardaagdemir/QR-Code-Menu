"use client";

import { useEffect, useId, useState } from "react";
import {
  createAnnouncement,
  endAnnouncement,
  listAnnouncements,
  type StaffAnnouncement,
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

/** Active-branch announcement management; no branch is selected by the browser. */
export default function AnnouncementsPage() {
  const { showToast } = useToast();
  const dialogTitleId = useId();
  const [announcements, setAnnouncements] = useState<StaffAnnouncement[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [message, setMessage] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  function load() {
    listAnnouncements()
      .then((items) => {
        setAnnouncements(items);
        setError(null);
      })
      .catch(() => setError("Duyurular yüklenemedi."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    if (!title.trim() || !message.trim()) return;
    setBusy(true);
    setFormError(null);
    try {
      await createAnnouncement(
        title.trim(),
        message.trim(),
        expiresAt.trim() === "" ? null : new Date(expiresAt).toISOString(),
      );
      setTitle("");
      setMessage("");
      setExpiresAt("");
      setCreateOpen(false);
      load();
      showToast("Duyuru yayınlandı.", "success");
    } catch {
      setFormError("Duyuru oluşturulamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function handleEnd(announcement: StaffAnnouncement) {
    setBusy(true);
    try {
      await endAnnouncement(announcement.id);
      load();
      showToast("Duyuru sonlandırıldı.", "success");
    } catch {
      showToast("Duyuru sonlandırılamadı.", "error");
    } finally {
      setBusy(false);
    }
  }

  function isActive(announcement: StaffAnnouncement): boolean {
    return announcement.expiresAt === null || new Date(announcement.expiresAt) > new Date();
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader
          title="Duyurular"
          description="Yalnızca aktif şubenizdeki personele gösterilir."
          actions={<Button onClick={() => setCreateOpen(true)}>+ Duyuru Yayınla</Button>}
        />
        {loading ? <TableSkeleton /> : error ? (
          <ErrorState message={error} onRetry={load} />
        ) : announcements.length === 0 ? (
          <EmptyState title="Henüz duyuru yok" />
        ) : (
          <Table>
            <thead><tr><th>Başlık</th><th>Mesaj</th><th>Durum</th><th></th></tr></thead>
            <tbody>
              {announcements.map((announcement) => (
                <tr key={announcement.id}>
                  <td className={tableStyles.primary}>{announcement.title}</td>
                  <td className={tableStyles.muted}>{announcement.message}</td>
                  <td><Badge tone={isActive(announcement) ? "neutral" : "danger"}>{isActive(announcement) ? "Aktif" : "Sona erdi"}</Badge></td>
                  <td>{isActive(announcement) ? <Button size="md" variant="ghost" disabled={busy} onClick={() => handleEnd(announcement)}>Sonlandır</Button> : null}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </main>

      {createOpen ? (
        <Dialog onClose={() => setCreateOpen(false)} labelledBy={dialogTitleId}>
          <h2 id={dialogTitleId} className={styles.sectionTitle}>Yeni Duyuru</h2>
          <form className={styles.section} onSubmit={handleCreate}>
            <FormField label="Başlık" required>{(props) => <Input {...props} value={title} onChange={(event) => setTitle(event.target.value)} required />}</FormField>
            <FormField label="Mesaj" required>{(props) => <Input {...props} value={message} onChange={(event) => setMessage(event.target.value)} required />}</FormField>
            <FormField label="Bitiş tarihi (opsiyonel)">{(props) => <Input {...props} type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} />}</FormField>
            {formError ? <ErrorState message={formError} /> : null}
            <Button type="submit" disabled={busy}>{busy ? "Yayınlanıyor…" : "Duyuru Yayınla"}</Button>
          </form>
        </Dialog>
      ) : null}
    </AppShell>
  );
}
