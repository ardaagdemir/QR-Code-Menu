"use client";

import { useEffect, useId, useState } from "react";
import { Eye, EyeOff, KeyRound, Pencil, Power, UserPlus, Users } from "lucide-react";
import {
  activateStaffUser,
  ApiError,
  changeStaffUserRole,
  createStaffUser,
  deactivateStaffUser,
  listStaffUsers,
  me,
  resetStaffUserPassword,
  updateStaffUserEmail,
  MIN_PASSWORD_LENGTH,
  type StaffRole,
  type StaffUser,
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
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import { useToast } from "@/components/ui/ToastProvider";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";
import pageStyles from "./page.module.css";

const ROLE_LABELS: Record<string, string> = {
  BUSINESS_ADMIN: "İşletme Yöneticisi",
  BRANCH_MANAGER: "Şube Sorumlusu",
  CASHIER: "Kasa Personeli",
};

/** Section 4, staff-web admin screen #4: Personel/Rol yönetimi (Permission.STAFF_MANAGE). */
export default function StaffPage() {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [staffUsers, setStaffUsers] = useState<StaffUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentStaffUserId, setCurrentStaffUserId] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [role, setRole] = useState<StaffRole>("CASHIER");
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<StaffUser | null>(null);
  const [deactivating, setDeactivating] = useState(false);

  const [activateTarget, setActivateTarget] = useState<StaffUser | null>(null);
  const [activating, setActivating] = useState(false);

  const [editTarget, setEditTarget] = useState<StaffUser | null>(null);
  const [editEmail, setEditEmail] = useState("");
  const [editRole, setEditRole] = useState<StaffRole>("CASHIER");
  const [editing, setEditing] = useState(false);
  const [editFormError, setEditFormError] = useState<string | null>(null);
  const editDialogTitleId = useId();

  const [resetTarget, setResetTarget] = useState<StaffUser | null>(null);
  const [resetPassword, setResetPassword] = useState("");
  const [resetConfirmPassword, setResetConfirmPassword] = useState("");
  const [resetPasswordVisible, setResetPasswordVisible] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [resetFormError, setResetFormError] = useState<string | null>(null);
  const resetDialogTitleId = useId();

  function load() {
    Promise.all([listStaffUsers(), me()])
      .then(([users, context]) => {
        setStaffUsers(users);
        setCurrentStaffUserId(context.staffUserId);
        setError(null);
      })
      .catch(() => setError("Personel listesi yüklenemedi."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    if (!email.trim() || password.length < MIN_PASSWORD_LENGTH) {
      setFormError(`E-posta girin ve şifre en az ${MIN_PASSWORD_LENGTH} karakter olsun.`);
      return;
    }
    setCreating(true);
    setFormError(null);
    try {
      await createStaffUser(email.trim(), password, role);
      setEmail("");
      setPassword("");
      setPasswordVisible(false);
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

  async function handleConfirmActivate() {
    if (!activateTarget) {
      return;
    }
    setActivating(true);
    try {
      await activateStaffUser(activateTarget.id);
      load();
      showToast("Personel aktifleştirildi.", "success");
    } catch {
      showToast("Personel aktifleştirilemedi.", "error");
    } finally {
      setActivating(false);
      setActivateTarget(null);
    }
  }

  function openEditDialog(user: StaffUser) {
    setEditFormError(null);
    setEditEmail(user.email);
    setEditRole(user.role as StaffRole);
    setEditTarget(user);
  }

  async function handleConfirmEdit(event: React.FormEvent) {
    event.preventDefault();
    if (!editTarget) {
      return;
    }
    const trimmedEmail = editEmail.trim();
    if (!trimmedEmail) {
      setEditFormError("E-posta girin.");
      return;
    }
    const emailChanged = trimmedEmail.toLowerCase() !== editTarget.email.toLowerCase();
    const roleChanged = editRole !== editTarget.role;
    if (!emailChanged && !roleChanged) {
      setEditTarget(null);
      return;
    }
    setEditing(true);
    setEditFormError(null);
    try {
      if (emailChanged) {
        await updateStaffUserEmail(editTarget.id, trimmedEmail);
      }
      if (roleChanged) {
        await changeStaffUserRole(editTarget.id, editRole);
      }
      setEditTarget(null);
      load();
      showToast("Personel güncellendi.", "success");
    } catch (err) {
      // Email and role are two separate requests - one may have already succeeded before the
      // other failed (e.g. last-active-BUSINESS_ADMIN protection blocking only the role change),
      // so refresh the list to reflect whatever partially applied.
      load();
      if (err instanceof ApiError && err.status === 409) {
        setEditFormError("Bu e-posta zaten kullanımda ya da bu değişiklik son aktif İşletme Yöneticisi'ni etkiliyor.");
      } else {
        setEditFormError("Personel güncellenemedi.");
      }
    } finally {
      setEditing(false);
    }
  }

  function openResetDialog(user: StaffUser) {
    setResetFormError(null);
    setResetPassword("");
    setResetConfirmPassword("");
    setResetPasswordVisible(false);
    setResetTarget(user);
  }

  async function handleConfirmReset(event: React.FormEvent) {
    event.preventDefault();
    if (!resetTarget) {
      return;
    }
    if (resetPassword.length < MIN_PASSWORD_LENGTH) {
      setResetFormError(`Şifre en az ${MIN_PASSWORD_LENGTH} karakter olsun.`);
      return;
    }
    if (resetPassword !== resetConfirmPassword) {
      setResetFormError("Şifreler eşleşmiyor.");
      return;
    }
    setResetting(true);
    setResetFormError(null);
    try {
      await resetStaffUserPassword(resetTarget.id, resetPassword, resetConfirmPassword);
      setResetTarget(null);
      showToast("Şifre sıfırlandı.", "success");
    } catch {
      setResetFormError("Şifre sıfırlanamadı.");
    } finally {
      setResetting(false);
    }
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader
          title="Personel"
          description="Ekip üyelerini, erişim rollerini ve hesap durumlarını yönetin."
          actions={
            <Button
              onClick={() => {
                setFormError(null);
                setPasswordVisible(false);
                setCreateOpen(true);
              }}
            >
              <UserPlus size={17} aria-hidden="true" /> Personel Ekle
            </Button>
          }
        />

        <section className={`${styles.section} ${styles.panel} ${pageStyles.tablePanel}`}>
          <div className={pageStyles.panelHeader}>
            <h2 className={styles.sectionTitle}>Ekip Listesi</h2>
            <p className={styles.rowMeta}>{staffUsers.length} personel hesabı</p>
          </div>
          <div className={pageStyles.panelBody}>
            {loading ? (
              <TableSkeleton />
            ) : error ? (
              <ErrorState message={error} onRetry={load} />
            ) : staffUsers.length === 0 ? (
              <EmptyState icon={<Users size={20} />} title="Henüz personel yok" description="İlk ekip üyesini ekleyin." />
            ) : (
              <Table>
                <thead>
                  <tr>
                    <th>E-posta</th>
                    <th>Rol</th>
                    <th>Durum</th>
                    <th className={pageStyles.actionsHeader}>İşlemler</th>
                  </tr>
                </thead>
                <tbody>
                  {staffUsers.map((user) => (
                    <tr key={user.id}>
                      <td>
                        <div className={pageStyles.identity}>
                          <span className={pageStyles.avatar} aria-hidden="true">{user.email.slice(0, 2).toUpperCase()}</span>
                          <span className={tableStyles.primary}>{user.email}</span>
                        </div>
                      </td>
                      <td>{ROLE_LABELS[user.role] ?? user.role}</td>
                      <td>
                        <Badge tone={user.active ? "success" : "danger"}>{user.active ? "Aktif" : "Devre dışı"}</Badge>
                      </td>
                      <td className={pageStyles.actionsCell}>
                        {user.id === currentStaffUserId ? (
                          <span className={styles.rowMeta}>-</span>
                        ) : (
                          <div className={`${tableStyles.actions} ${pageStyles.staffActions}`}>
                            <Button size="md" variant="ghost" onClick={() => openEditDialog(user)}>
                              <Pencil size={15} aria-hidden="true" /> Düzenle
                            </Button>
                            <Button size="md" variant="ghost" onClick={() => openResetDialog(user)}>
                              <KeyRound size={15} aria-hidden="true" /> Şifre Sıfırla
                            </Button>
                            {user.active ? (
                              <Button className={pageStyles.dangerAction} size="md" variant="ghost" onClick={() => setDeactivateTarget(user)}>
                                <Power size={15} aria-hidden="true" /> Devre Dışı Bırak
                              </Button>
                            ) : (
                              <Button size="md" variant="ghost" onClick={() => setActivateTarget(user)}>
                                <Power size={15} aria-hidden="true" /> Aktifleştir
                              </Button>
                            )}
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
          </div>
        </section>
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
                <div className={pageStyles.passwordField}>
                  <Input
                    {...controlProps}
                    type={passwordVisible ? "text" : "password"}
                    className={pageStyles.passwordInput}
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    autoComplete="new-password"
                    required
                  />
                  <button
                    type="button"
                    className={pageStyles.passwordToggle}
                    onClick={() => setPasswordVisible((visible) => !visible)}
                    aria-label={passwordVisible ? "Şifreyi gizle" : "Şifreyi göster"}
                    aria-pressed={passwordVisible}
                    disabled={creating}
                  >
                    {passwordVisible ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
                  </button>
                </div>
              )}
            </FormField>
            <FormField label="Rol">
              {(controlProps) => (
                <Select {...controlProps} value={role} onChange={(event) => setRole(event.target.value as StaffRole)}>
                  <option value="BUSINESS_ADMIN">İşletme Yöneticisi</option>
                  <option value="BRANCH_MANAGER">Şube Sorumlusu</option>
                  <option value="CASHIER">Kasa Personeli</option>
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

      {resetTarget ? (
        <Dialog onClose={() => setResetTarget(null)} labelledBy={resetDialogTitleId}>
          <h2 id={resetDialogTitleId} className={styles.sectionTitle}>
            Şifre Sıfırla
          </h2>
          <form className={styles.section} onSubmit={handleConfirmReset}>
            <p className={styles.rowMeta}>
              {`"${resetTarget.email}" için yeni bir geçici şifre belirleyin. Bu şifreyi personelle güvenli bir şekilde paylaşın.`}
            </p>
            <FormField label="Yeni Geçici Şifre" hint={`En az ${MIN_PASSWORD_LENGTH} karakter`} required>
              {(controlProps) => (
                <div className={pageStyles.passwordField}>
                  <Input
                    {...controlProps}
                    type={resetPasswordVisible ? "text" : "password"}
                    className={pageStyles.passwordInput}
                    value={resetPassword}
                    onChange={(event) => setResetPassword(event.target.value)}
                    autoComplete="new-password"
                    required
                  />
                  <button
                    type="button"
                    className={pageStyles.passwordToggle}
                    onClick={() => setResetPasswordVisible((visible) => !visible)}
                    aria-label={resetPasswordVisible ? "Şifreyi gizle" : "Şifreyi göster"}
                    aria-pressed={resetPasswordVisible}
                    disabled={resetting}
                  >
                    {resetPasswordVisible ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
                  </button>
                </div>
              )}
            </FormField>
            <FormField label="Yeni Şifre (Tekrar)" required>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  type={resetPasswordVisible ? "text" : "password"}
                  value={resetConfirmPassword}
                  onChange={(event) => setResetConfirmPassword(event.target.value)}
                  autoComplete="new-password"
                  required
                />
              )}
            </FormField>

            {resetFormError ? <ErrorState message={resetFormError} /> : null}

            <Button type="submit" disabled={resetting}>
              {resetting ? "Sıfırlanıyor…" : "Şifreyi Sıfırla"}
            </Button>
          </form>
        </Dialog>
      ) : null}

      {deactivateTarget ? (
        <ConfirmDialog
          title="Devre Dışı Bırak"
          message={`"${deactivateTarget.email}" devre dışı bırakılacak ve artık giriş yapamayacak.`}
          confirmLabel="Devre Dışı Bırak"
          tone="danger"
          confirmLoading={deactivating}
          onConfirm={handleConfirmDeactivate}
          onCancel={() => setDeactivateTarget(null)}
        />
      ) : null}

      {activateTarget ? (
        <ConfirmDialog
          title="Aktifleştir"
          message={`"${activateTarget.email}" yeniden aktifleştirilecek ve tekrar giriş yapabilecek. Şifresi değişmez.`}
          confirmLabel="Aktifleştir"
          confirmLoading={activating}
          onConfirm={handleConfirmActivate}
          onCancel={() => setActivateTarget(null)}
        />
      ) : null}

      {editTarget ? (
        <Dialog onClose={() => setEditTarget(null)} labelledBy={editDialogTitleId}>
          <h2 id={editDialogTitleId} className={styles.sectionTitle}>
            Personeli Düzenle
          </h2>
          <form className={styles.section} onSubmit={handleConfirmEdit}>
            <FormField label="E-posta" required>
              {(controlProps) => (
                <Input {...controlProps} type="email" value={editEmail} onChange={(event) => setEditEmail(event.target.value)} required />
              )}
            </FormField>
            <FormField label="Rol">
              {(controlProps) => (
                <Select {...controlProps} value={editRole} onChange={(event) => setEditRole(event.target.value as StaffRole)}>
                  <option value="BUSINESS_ADMIN">İşletme Yöneticisi</option>
                  <option value="BRANCH_MANAGER">Şube Sorumlusu</option>
                  <option value="CASHIER">Kasa Personeli</option>
                </Select>
              )}
            </FormField>

            {editFormError ? <ErrorState message={editFormError} /> : null}

            <Button type="submit" disabled={editing}>
              {editing ? "Kaydediliyor…" : "Kaydet"}
            </Button>
          </form>
        </Dialog>
      ) : null}
    </AppShell>
  );
}
