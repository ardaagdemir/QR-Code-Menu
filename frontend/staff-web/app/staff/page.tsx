"use client";

import { useEffect, useId, useState } from "react";
import { createStaffUser, deactivateStaffUser, listStaffUsers, type StaffRole, type StaffUser } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Dialog from "@/components/ui/Dialog";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import { useToast } from "@/components/ui/ToastProvider";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";

const ROLE_LABELS: Record<string, string> = {
  BUSINESS_ADMIN: "İşletme Yöneticisi",
  BRANCH_MANAGER: "Şube Sorumlusu",
  CASHIER: "Kasa",
};

/** Section 4, staff-web admin screen #4: Personel/Rol yönetimi (Permission.STAFF_MANAGE). */
export default function StaffPage() {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [staffUsers, setStaffUsers] = useState<StaffUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<StaffRole>("CASHIER");
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<StaffUser | null>(null);
  const [deactivating, setDeactivating] = useState(false);

  function load() {
    listStaffUsers()
      .then((users) => {
        setStaffUsers(users);
        setError(null);
      })
      .catch(() => setError("Personel listesi yüklenemedi."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    if (!email.trim() || password.length < 8) {
      setFormError("E-posta girin ve şifre en az 8 karakter olsun.");
      return;
    }
    setCreating(true);
    setFormError(null);
    try {
      await createStaffUser(email.trim(), password, role);
      setEmail("");
      setPassword("");
      setCreateOpen(false);
      load();
      showToast("Personel oluşturuldu.", "success");
    } catch {
      setFormError("Personel oluşturulamadı (e-posta zaten kullanımda olabilir).");
    } finally {
      setCreating(false);
    }
  }

  async function handleConfirmDeactivate() {
    if (!deactivateTarget) {
      return;
    }
    setDeactivating(true);
    try {
      await deactivateStaffUser(deactivateTarget.id);
      load();
      showToast("Personel devre dışı bırakıldı.", "success");
    } catch {
      showToast("Personel devre dışı bırakılamadı.", "error");
    } finally {
      setDeactivating(false);
      setDeactivateTarget(null);
    }
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader
          title="Personel & Rol Yönetimi"
          actions={
            <Button
              onClick={() => {
                setFormError(null);
                setCreateOpen(true);
              }}
            >
              + Personel Ekle
            </Button>
          }
        />

        {loading ? (
          <TableSkeleton />
        ) : error ? (
          <ErrorState message={error} onRetry={load} />
        ) : staffUsers.length === 0 ? (
          <EmptyState title="Henüz personel yok" />
        ) : (
          <Table>
            <thead>
              <tr>
                <th>E-posta</th>
                <th>Rol</th>
                <th>Durum</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {staffUsers.map((user) => (
                <tr key={user.id}>
                  <td className={tableStyles.primary}>{user.email}</td>
                  <td>{ROLE_LABELS[user.role] ?? user.role}</td>
                  <td>
                    <Badge tone={user.active ? "neutral" : "danger"}>{user.active ? "Aktif" : "Devre dışı"}</Badge>
                  </td>
                  <td>
                    {user.active ? (
                      <div className={tableStyles.actions}>
                        <Button size="md" variant="ghost" onClick={() => setDeactivateTarget(user)}>
                          Devre Dışı Bırak
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
            Yeni Personel
          </h2>
          <form className={styles.section} onSubmit={handleCreate}>
            <FormField label="E-posta" required>
              {(controlProps) => <Input {...controlProps} type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />}
            </FormField>
            <FormField label="Şifre" hint="En az 8 karakter" required>
              {(controlProps) => (
                <Input {...controlProps} type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
              )}
            </FormField>
            <FormField label="Rol">
              {(controlProps) => (
                <Select {...controlProps} value={role} onChange={(event) => setRole(event.target.value as StaffRole)}>
                  <option value="BUSINESS_ADMIN">İşletme Yöneticisi</option>
                  <option value="BRANCH_MANAGER">Şube Sorumlusu</option>
                  <option value="CASHIER">Kasa</option>
                </Select>
              )}
            </FormField>

            {formError ? <ErrorState message={formError} /> : null}

            <Button type="submit" disabled={creating}>
              {creating ? "Oluşturuluyor…" : "Personel Ekle"}
            </Button>
          </form>
        </Dialog>
      ) : null}

      {deactivateTarget ? (
        <ConfirmDialog
          title="Personeli Devre Dışı Bırak"
          message={`"${deactivateTarget.email}" devre dışı bırakılacak ve artık giriş yapamayacak. Bu işlem geri alınamaz.`}
          confirmLabel="Devre Dışı Bırak"
          tone="danger"
          confirmLoading={deactivating}
          onConfirm={handleConfirmDeactivate}
          onCancel={() => setDeactivateTarget(null)}
        />
      ) : null}
    </AppShell>
  );
}
