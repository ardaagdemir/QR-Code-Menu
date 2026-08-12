"use client";

import { useEffect, useState } from "react";
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
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import styles from "@/styles/admin.module.css";

/**
 * Section 18.1 "Şube duyuruları" (gap-analysis #7): fully manual, staff-authored
 * announcements shown as a banner on every staff-web page (Permission.ANNOUNCEMENT_MANAGE).
 */
export default function AnnouncementsPage() {
  const [announcements, setAnnouncements] = useState<StaffAnnouncement[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [title, setTitle] = useState("");
  const [message, setMessage] = useState("");
  const [target, setTarget] = useState<BranchAssignmentTarget>("ALL_BRANCHES");
  const [selectedBranchIds, setSelectedBranchIds] = useState<Set<string>>(new Set());
  const [expiresAt, setExpiresAt] = useState("");

  async function reload() {
    try {
      setAnnouncements(await listAnnouncements());
    } catch {
      setError("Duyurular yüklenemedi.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    Promise.all([listAnnouncements(), listBranches()])
      .then(([announcementList, branchList]) => {
        setAnnouncements(announcementList);
        setBranches(branchList);
      })
      .catch(() => setError("Duyurular yüklenemedi."))
      .finally(() => setLoading(false));
  }, []);

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
      setError("Seçili şubeler için en az bir şube seçin.");
      return;
    }
    setBusy(true);
    setError(null);
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
      await reload();
    } catch {
      setError("Duyuru oluşturulamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function handleEnd(announcement: StaffAnnouncement) {
    setBusy(true);
    setError(null);
    try {
      await endAnnouncement(announcement.id);
      await reload();
    } catch {
      setError("Duyuru sonlandırılamadı.");
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
        <div className={styles.header}>
          <h1 className={styles.title}>Duyurular</h1>
        </div>

        {error ? <p className={styles.error}>{error}</p> : null}

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Yeni Duyuru</h2>
          <form className={styles.form} onSubmit={handleCreate}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="announcement-title">
                Başlık
              </label>
              <input
                id="announcement-title"
                className={styles.input}
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                required
              />
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="announcement-message">
                Mesaj
              </label>
              <input
                id="announcement-message"
                className={styles.input}
                value={message}
                onChange={(event) => setMessage(event.target.value)}
                required
              />
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="announcement-expires">
                Bitiş tarihi (opsiyonel)
              </label>
              <input
                id="announcement-expires"
                type="datetime-local"
                className={styles.input}
                value={expiresAt}
                onChange={(event) => setExpiresAt(event.target.value)}
              />
            </div>
            <Button type="submit" disabled={busy}>
              Duyuru Yayınla
            </Button>
          </form>

          <div className={styles.field}>
            <span className={styles.label}>Hedef</span>
            <div className={styles.rowActions}>
              <label className={styles.rowMeta}>
                <input
                  type="radio"
                  name="announcement-target"
                  checked={target === "ALL_BRANCHES"}
                  onChange={() => setTarget("ALL_BRANCHES")}
                />{" "}
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
                    <input
                      type="checkbox"
                      checked={selectedBranchIds.has(branch.id)}
                      onChange={() => toggleBranch(branch.id)}
                    />{" "}
                    {branch.name}
                  </label>
                ))}
              </div>
            </div>
          ) : null}
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Duyuru Geçmişi</h2>
          <div className={styles.list}>
            {loading ? (
              <p className={styles.empty}>Yükleniyor…</p>
            ) : announcements.length === 0 ? (
              <p className={styles.empty}>Henüz duyuru yok.</p>
            ) : (
              announcements.map((announcement) => (
                <div key={announcement.id} className={styles.row}>
                  <div className={styles.rowMain}>
                    <span className={styles.rowTitle}>{announcement.title}</span>
                    <span className={styles.rowMeta}>
                      {announcement.message}
                      {" · "}
                      {announcement.target === "ALL_BRANCHES" ? "Tüm şubeler" : `${announcement.branchIds.length} şube`}
                    </span>
                  </div>
                  <div className={styles.rowActions}>
                    <Badge tone={isActive(announcement) ? "neutral" : "danger"}>
                      {isActive(announcement) ? "Aktif" : "Sona erdi"}
                    </Badge>
                    {isActive(announcement) ? (
                      <Button size="md" variant="ghost" disabled={busy} onClick={() => handleEnd(announcement)}>
                        Sonlandır
                      </Button>
                    ) : null}
                  </div>
                </div>
              ))
            )}
          </div>
        </section>
      </main>
    </AppShell>
  );
}
