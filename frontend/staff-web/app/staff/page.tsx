"use client";

import { useEffect, useState } from "react";
import { createStaffUser, deactivateStaffUser, listBranches, listStaffUsers, type Branch, type StaffRole, type StaffUser } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import styles from "@/styles/admin.module.css";

const ROLE_LABELS: Record<string, string> = {
  BUSINESS_ADMIN: "İşletme Yöneticisi",
  BRANCH_MANAGER: "Şube Sorumlusu",
  CASHIER: "Kasa",
  KITCHEN_STAFF: "Mutfak Personeli",
};

/** Section 4, staff-web admin screen #4: Personel/Rol yönetimi (Permission.STAFF_MANAGE). */
export default function StaffPage() {
  const [staffUsers, setStaffUsers] = useState<StaffUser[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [loading, setLoading] = useState(true);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<StaffRole>("KITCHEN_STAFF");
  const [selectedBranchIds, setSelectedBranchIds] = useState<string[]>([]);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function reload() {
    try {
      const [users, branchList] = await Promise.all([listStaffUsers(), listBranches()]);
      setStaffUsers(users);
      setBranches(branchList);
    } catch {
      setError("Personel listesi yüklenemedi.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    async function fetchStaff() {
      try {
        const [users, branchList] = await Promise.all([listStaffUsers(), listBranches()]);
        if (!cancelled) {
          setStaffUsers(users);
          setBranches(branchList);
        }
      } catch {
        if (!cancelled) {
          setError("Personel listesi yüklenemedi.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void fetchStaff();
    return () => {
      cancelled = true;
    };
  }, []);

  function toggleBranch(branchId: string) {
    setSelectedBranchIds((current) => (current.includes(branchId) ? current.filter((id) => id !== branchId) : [...current, branchId]));
  }

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    if (!email.trim() || password.length < 8) {
      setError("E-posta girin ve şifre en az 8 karakter olsun.");
      return;
    }
    setCreating(true);
    setError(null);
    try {
      await createStaffUser(email.trim(), password, role, role === "BUSINESS_ADMIN" ? [] : selectedBranchIds);
      setEmail("");
      setPassword("");
      setSelectedBranchIds([]);
      await reload();
    } catch {
      setError("Personel oluşturulamadı (e-posta zaten kullanımda olabilir).");
    } finally {
      setCreating(false);
    }
  }

  async function handleDeactivate(staffUserId: string) {
    setError(null);
    try {
      await deactivateStaffUser(staffUserId);
      await reload();
    } catch {
      setError("Personel devre dışı bırakılamadı.");
    }
  }

  function branchNames(branchIds: string[]): string {
    if (branchIds.length === 0) {
      return "Tüm şubeler";
    }
    return branchIds.map((id) => branches.find((b) => b.id === id)?.name ?? id).join(", ");
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>Personel &amp; Rol Yönetimi</h1>
        </div>

        <form className={styles.section} onSubmit={handleCreate}>
          <h2 className={styles.sectionTitle}>Yeni Personel</h2>
          <div className={styles.form}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="staff-email">
                E-posta
              </label>
              <input id="staff-email" type="email" className={styles.input} value={email} onChange={(event) => setEmail(event.target.value)} required />
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="staff-password">
                Şifre
              </label>
              <input
                id="staff-password"
                type="password"
                className={styles.input}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="staff-role">
                Rol
              </label>
              <select id="staff-role" className={styles.select} value={role} onChange={(event) => setRole(event.target.value as StaffRole)}>
                <option value="BUSINESS_ADMIN">İşletme Yöneticisi</option>
                <option value="BRANCH_MANAGER">Şube Sorumlusu</option>
                <option value="CASHIER">Kasa</option>
                <option value="KITCHEN_STAFF">Mutfak Personeli</option>
              </select>
            </div>
            <Button type="submit" disabled={creating}>
              {creating ? "Oluşturuluyor…" : "Personel Ekle"}
            </Button>
          </div>

          {role !== "BUSINESS_ADMIN" && branches.length > 0 ? (
            <div className={styles.field}>
              <span className={styles.label}>Şubeler</span>
              <div className={styles.rowActions}>
                {branches.map((branch) => (
                  <label key={branch.id} style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                    <input
                      type="checkbox"
                      checked={selectedBranchIds.includes(branch.id)}
                      onChange={() => toggleBranch(branch.id)}
                    />
                    {branch.name}
                  </label>
                ))}
              </div>
            </div>
          ) : null}
        </form>

        {error ? <p className={styles.error}>{error}</p> : null}

        <div className={styles.list}>
          {loading ? (
            <p className={styles.empty}>Yükleniyor…</p>
          ) : staffUsers.length === 0 ? (
            <p className={styles.empty}>Henüz personel yok.</p>
          ) : (
            staffUsers.map((user) => (
              <div key={user.id} className={styles.row}>
                <div className={styles.rowMain}>
                  <span className={styles.rowTitle}>{user.email}</span>
                  <span className={styles.rowMeta}>
                    {ROLE_LABELS[user.role] ?? user.role} · {branchNames(user.branchIds)}
                  </span>
                </div>
                <div className={styles.rowActions}>
                  <Badge tone={user.active ? "neutral" : "danger"}>{user.active ? "Aktif" : "Devre dışı"}</Badge>
                  {user.active ? (
                    <Button size="md" variant="ghost" onClick={() => handleDeactivate(user.id)}>
                      Devre Dışı Bırak
                    </Button>
                  ) : null}
                </div>
              </div>
            ))
          )}
        </div>
      </main>
    </AppShell>
  );
}
