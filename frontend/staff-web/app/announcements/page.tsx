"use client";

import { useEffect, useId, useState } from "react";
import {
  createAnnouncement,
  endAnnouncement,
  listAnnouncements,
  listBranches,
  type Branch,
  type BranchAssignmentTarget,
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

/**
 * Section 18.1 "Şube duyuruları" (gap-analysis #7): fully manual, staff-authored
 * announcements shown as a banner on every staff-web page (Permission.ANNOUNCEMENT_MANAGE).
 */
export default function AnnouncementsPage() {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [announcements, setAnnouncements] = useState<StaffAnnouncement[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [createOpen, setCreateOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [message, setMessage] = useState("");
  const [target, setTarget] = useState<BranchAssignmentTarget>("ALL_BRANCHES");
  const [selectedBranchIds, setSelectedBranchIds] = useState<Set<string>>(new Set());
  const [expiresAt, setExpiresAt] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  function load() {
    Promise.all([listAnnouncements(), listBranches()])
      .then(([announcementList, branchList]) => {
        setAnnouncements(announcementList);
        setBranches(branchList);
        setError(null);
      })
      .catch(() => setError("Duyurular yüklenemedi."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  function toggleBranch(branchId: string) {
    setSelectedBranchIds((current) => {
      const next = new Set(current);
      if (next.has(branchId)) {
        next.delete(branchId);
      } else {
        next.add(branchId);
      }
      return next;
    });
  }

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    if (!title.trim() || !message.trim()) {
      return;
    }
    if (target === "SELECTED_BRANCHES" && selectedBranchIds.size === 0) {
      setFormError("Seçili şubeler için en az bir şube seçin.");
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      await createAnnouncement(
        title.trim(),
        message.trim(),
        target,
        Array.from(selectedBranchIds),
        expiresAt.trim() === "" ? null : new Date(expiresAt).toISOString(),
      );
      setTitle("");
      setMessage("");
      setTarget("ALL_BRANCHES");
      setSelectedBranchIds(new Set());
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
          actions={
            <Button
              onClick={() => {
                setFormError(null);
                setCreateOpen(true);
              }}
            >
              + Duyuru Yayınla
            </Button>
          }
        />

        {loading ? (
          <TableSkeleton />
        ) : error ? (
          <ErrorState message={error} onRetry={load} />
        ) : announcements.length === 0 ? (
          <EmptyState title="Henüz duyuru yok" />
        ) : (
          <Table>
            <thead>
              <tr>
                <th>Başlık</th>
                <th>Mesaj</th>
                <th>Hedef</th>
                <th>Durum</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {announcements.map((announcement) => (
                <tr key={announcement.id}>
                  <td className={tableStyles.primary}>{announcement.title}</td>
                  <td className={tableStyles.muted}>{announcement.message}</td>
                  <td>{announcement.target === "ALL_BRANCHES" ? "Tüm şubeler" : `${announcement.branchIds.length} şube`}</td>
                  <td>
                    <Badge tone={isActive(announcement) ? "neutral" : "danger"}>{isActive(announcement) ? "Aktif" : "Sona erdi"}</Badge>
                  </td>
                  <td>
                    {isActive(announcement) ? (
                      <div className={tableStyles.actions}>
                        <Button size="md" variant="ghost" disabled={busy} onClick={() => handleEnd(announcement)}>
                          Sonlandır
                        </Button>
                      </div>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </main>

      {createOpen ? (
        <Dialog onClose={() => setCreateOpen(false)} labelledBy={dialogTitleId}>
          <h2 id={dialogTitleId} className={styles.sectionTitle}>
            Yeni Duyuru
          </h2>
          <form className={styles.section} onSubmit={handleCreate}>
            <FormField label="Başlık" required>
              {(controlProps) => <Input {...controlProps} value={title} onChange={(event) => setTitle(event.target.value)} required />}
            </FormField>
            <FormField label="Mesaj" required>
              {(controlProps) => <Input {...controlProps} value={message} onChange={(event) => setMessage(event.target.value)} required />}
            </FormField>
            <FormField label="Bitiş tarihi (opsiyonel)">
              {(controlProps) => (
                <Input {...controlProps} type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} />
              )}
            </FormField>

            <div className={styles.field}>
              <span className={styles.label}>Hedef</span>
              <div className={styles.rowActions}>
                <label className={styles.rowMeta}>
                  <input type="radio" name="announcement-target" checked={target === "ALL_BRANCHES"} onChange={() => setTarget("ALL_BRANCHES")} />{" "}
                  Tüm şubeler
                </label>
                <label className={styles.rowMeta}>
                  <input
                    type="radio"
                    name="announcement-target"
                    checked={target === "SELECTED_BRANCHES"}
                    onChange={() => setTarget("SELECTED_BRANCHES")}
                  />{" "}
                  Seçili şubeler
                </label>
              </div>
            </div>

            {target === "SELECTED_BRANCHES" ? (
              <div className={styles.field}>
                <span className={styles.label}>Şubeler</span>
                <div className={styles.rowActions}>
                  {branches.map((branch) => (
                    <label key={branch.id} className={styles.rowMeta}>
                      <input type="checkbox" checked={selectedBranchIds.has(branch.id)} onChange={() => toggleBranch(branch.id)} /> {branch.name}
                    </label>
                  ))}
                </div>
              </div>
            ) : null}

            {formError ? <ErrorState message={formError} /> : null}

            <Button type="submit" disabled={busy}>
              {busy ? "Yayınlanıyor…" : "Duyuru Yayınla"}
            </Button>
          </form>
        </Dialog>
      ) : null}
    </AppShell>
  );
}
