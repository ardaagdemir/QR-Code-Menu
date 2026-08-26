"use client";

import { useEffect, useId, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Eye, EyeOff, KeyRound, Pencil, Plus, Store, Trash2, UserPlus, Users } from "lucide-react";
import {
  activatePlatformBranch,
  activatePlatformBusiness,
  activatePlatformStaffUser,
  ApiError,
  changePlatformStaffUserRole,
  createPlatformBranch,
  createPlatformStaffUser,
  deactivatePlatformBranch,
  deactivatePlatformBusiness,
  deactivatePlatformStaffUser,
  getPlatformBusiness,
  hardDeletePlatformStaffUser,
  listPlatformBranches,
  listPlatformStaffUsers,
  me,
  resetPlatformStaffUserPassword,
  updatePlatformBranchInfo,
  updatePlatformBusinessName,
  MIN_PASSWORD_LENGTH,
  type Branch,
  type Business,
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
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import { useToast } from "@/components/ui/ToastProvider";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";
import pageStyles from "../../platform-admin.module.css";

const ROLE_LABELS: Record<string, string> = {
  PLATFORM_ADMIN: "Platform Yöneticisi",
  BUSINESS_ADMIN: "İşletme Yöneticisi",
  BRANCH_MANAGER: "Şube Sorumlusu",
  CASHIER: "Kasa Personeli",
};

/** Platform Admin Panel - tek işletmenin detayı: aktif/pasif, şubeler, kullanıcılar.
 * PLATFORM_ADMIN only, /api/platform-admin/** üzerinden - businessId path'ten geliyor,
 * çağıranın kendi StaffUser.businessId'sinden bağımsız (gerçek cross-business rol). */
export default function PlatformAdminBusinessDetailPage() {
  const { businessId } = useParams<{ businessId: string }>();
  const { showToast } = useToast();
  const branchDialogTitleId = useId();
  const branchEditDialogTitleId = useId();
  const businessNameDialogTitleId = useId();
  const staffDialogTitleId = useId();
  const roleDialogTitleId = useId();
  const resetDialogTitleId = useId();
  const hardDeleteDialogTitleId = useId();

  const [business, setBusiness] = useState<Business | null>(null);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [staffUsers, setStaffUsers] = useState<StaffUser[]>([]);
  const [currentStaffUserId, setCurrentStaffUserId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [togglingBusiness, setTogglingBusiness] = useState(false);

  const [editingBusinessName, setEditingBusinessName] = useState(false);
  const [businessNameInput, setBusinessNameInput] = useState("");
  const [savingBusinessName, setSavingBusinessName] = useState(false);
  const [businessNameFormError, setBusinessNameFormError] = useState<string | null>(null);

  const [branchDialogOpen, setBranchDialogOpen] = useState(false);
  const [branchName, setBranchName] = useState("");
  const [creatingBranch, setCreatingBranch] = useState(false);
  const [branchFormError, setBranchFormError] = useState<string | null>(null);

  const [editingBranch, setEditingBranch] = useState<Branch | null>(null);
  const [editBranchName, setEditBranchName] = useState("");
  const [editBranchAddress, setEditBranchAddress] = useState("");
  const [savingBranchEdit, setSavingBranchEdit] = useState(false);
  const [editBranchFormError, setEditBranchFormError] = useState<string | null>(null);

  const [togglingBranchId, setTogglingBranchId] = useState<string | null>(null);

  const [staffDialogOpen, setStaffDialogOpen] = useState(false);
  const [staffEmail, setStaffEmail] = useState("");
  const [staffPassword, setStaffPassword] = useState("");
  const [staffPasswordVisible, setStaffPasswordVisible] = useState(false);
  const [staffRole, setStaffRole] = useState<StaffRole>("CASHIER");
  const [staffBranchId, setStaffBranchId] = useState("");
  const [creatingStaff, setCreatingStaff] = useState(false);
  const [staffFormError, setStaffFormError] = useState<string | null>(null);

  const [togglingStaffId, setTogglingStaffId] = useState<string | null>(null);

  const [roleTarget, setRoleTarget] = useState<StaffUser | null>(null);
  const [roleTargetRole, setRoleTargetRole] = useState<StaffRole>("CASHIER");
  const [changingRole, setChangingRole] = useState(false);

  const [resetTarget, setResetTarget] = useState<StaffUser | null>(null);
  const [resetPassword, setResetPassword] = useState("");
  const [resetConfirmPassword, setResetConfirmPassword] = useState("");
  const [resetPasswordVisible, setResetPasswordVisible] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [resetFormError, setResetFormError] = useState<string | null>(null);

  const [hardDeleteTarget, setHardDeleteTarget] = useState<StaffUser | null>(null);
  const [hardDeleteAcknowledged, setHardDeleteAcknowledged] = useState(false);
  const [hardDeleting, setHardDeleting] = useState(false);

  function load() {
    Promise.all([getPlatformBusiness(businessId), listPlatformBranches(businessId), listPlatformStaffUsers(businessId), me()])
      .then(([businessData, branchList, staffList, currentContext]) => {
        setBusiness(businessData);
        setBranches(branchList);
        setStaffUsers(staffList);
        setCurrentStaffUserId(currentContext.staffUserId);
        setError(null);
      })
      .catch(() => setError("İşletme detayı yüklenemedi."))
      .finally(() => setLoading(false));
  }

  useEffect(load, [businessId]);

  async function handleToggleBusinessActive() {
    if (!business) return;
    setTogglingBusiness(true);
    try {
      const updated = business.active ? await deactivatePlatformBusiness(business.id) : await activatePlatformBusiness(business.id);
      setBusiness(updated);
      showToast(updated.active ? "İşletme aktifleştirildi." : "İşletme pasifleştirildi.", "success");
    } catch {
      showToast("İşletme durumu güncellenemedi.", "error");
    } finally {
      setTogglingBusiness(false);
    }
  }

  function openEditBusinessNameDialog() {
    if (!business) return;
    setBusinessNameFormError(null);
    setBusinessNameInput(business.name);
    setEditingBusinessName(true);
  }

  async function handleSaveBusinessName(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = businessNameInput.trim();
    if (!trimmed) {
      setBusinessNameFormError("İşletme adı girin.");
      return;
    }
    setSavingBusinessName(true);
    setBusinessNameFormError(null);
    try {
      const updated = await updatePlatformBusinessName(businessId, trimmed);
      setBusiness(updated);
      setEditingBusinessName(false);
      showToast("İşletme adı güncellendi.", "success");
    } catch {
      setBusinessNameFormError("İşletme adı güncellenemedi.");
    } finally {
      setSavingBusinessName(false);
    }
  }

  async function handleCreateBranch(event: React.FormEvent) {
    event.preventDefault();
    if (!branchName.trim()) {
      setBranchFormError("Şube adı girin.");
      return;
    }
    setCreatingBranch(true);
    setBranchFormError(null);
    try {
      await createPlatformBranch(businessId, branchName.trim());
      setBranchName("");
      setBranchDialogOpen(false);
      load();
      showToast("Şube oluşturuldu.", "success");
    } catch {
      setBranchFormError("Şube oluşturulamadı.");
    } finally {
      setCreatingBranch(false);
    }
  }

  function openEditBranchDialog(branch: Branch) {
    setEditBranchFormError(null);
    setEditBranchName(branch.name);
    setEditBranchAddress(branch.address ?? "");
    setEditingBranch(branch);
  }

  async function handleSaveBranchEdit(event: React.FormEvent) {
    event.preventDefault();
    if (!editingBranch) return;
    if (!editBranchName.trim()) {
      setEditBranchFormError("Şube adı girin.");
      return;
    }
    setSavingBranchEdit(true);
    setEditBranchFormError(null);
    try {
      await updatePlatformBranchInfo(businessId, editingBranch.id, editBranchName.trim(), editBranchAddress.trim() || null);
      setEditingBranch(null);
      load();
      showToast("Şube güncellendi.", "success");
    } catch {
      setEditBranchFormError("Şube güncellenemedi.");
    } finally {
      setSavingBranchEdit(false);
    }
  }

  async function handleToggleBranchActive(branch: Branch) {
    setTogglingBranchId(branch.id);
    try {
      const updated = branch.active
        ? await deactivatePlatformBranch(businessId, branch.id)
        : await activatePlatformBranch(businessId, branch.id);
      setBranches((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      showToast(updated.active ? "Şube aktifleştirildi." : "Şube pasifleştirildi.", "success");
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        showToast("Şubede devam eden bir sipariş olduğu için pasife alınamadı.", "error");
      } else {
        showToast("Şube durumu güncellenemedi.", "error");
      }
    } finally {
      setTogglingBranchId(null);
    }
  }

  function openStaffDialog() {
    setStaffFormError(null);
    setStaffEmail("");
    setStaffPassword("");
    setStaffPasswordVisible(false);
    setStaffRole("CASHIER");
    setStaffBranchId(branches[0]?.id ?? "");
    setStaffDialogOpen(true);
  }

  async function handleCreateStaff(event: React.FormEvent) {
    event.preventDefault();
    if (!staffEmail.trim() || staffPassword.length < MIN_PASSWORD_LENGTH) {
      setStaffFormError(`E-posta girin ve şifre en az ${MIN_PASSWORD_LENGTH} karakter olsun.`);
      return;
    }
    if (!staffBranchId) {
      setStaffFormError("Bir şube seçin - personel tam olarak bir şubeye atanmalı.");
      return;
    }
    setCreatingStaff(true);
    setStaffFormError(null);
    try {
      await createPlatformStaffUser(businessId, staffEmail.trim(), staffPassword, staffRole, [staffBranchId]);
      setStaffDialogOpen(false);
      load();
      showToast("Kullanıcı oluşturuldu.", "success");
    } catch {
      setStaffFormError("Kullanıcı oluşturulamadı (e-posta zaten kullanımda olabilir).");
    } finally {
      setCreatingStaff(false);
    }
  }

  async function handleToggleStaffActive(user: StaffUser) {
    setTogglingStaffId(user.id);
    try {
      if (user.active) {
        await deactivatePlatformStaffUser(businessId, user.id);
      } else {
        await activatePlatformStaffUser(businessId, user.id);
      }
      load();
      showToast(user.active ? "Kullanıcı devre dışı bırakıldı." : "Kullanıcı aktifleştirildi.", "success");
    } catch {
      showToast("Kullanıcı durumu güncellenemedi.", "error");
    } finally {
      setTogglingStaffId(null);
    }
  }

  function openRoleDialog(user: StaffUser) {
    setRoleTarget(user);
    setRoleTargetRole((user.role as StaffRole) ?? "CASHIER");
  }

  async function handleChangeRole(event: React.FormEvent) {
    event.preventDefault();
    if (!roleTarget) return;
    setChangingRole(true);
    try {
      await changePlatformStaffUserRole(businessId, roleTarget.id, roleTargetRole);
      setRoleTarget(null);
      load();
      showToast("Rol güncellendi.", "success");
    } catch {
      showToast("Rol güncellenemedi.", "error");
    } finally {
      setChangingRole(false);
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
    if (!resetTarget) return;
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
      await resetPlatformStaffUserPassword(businessId, resetTarget.id, resetPassword, resetConfirmPassword);
      setResetTarget(null);
      showToast("Şifre sıfırlandı.", "success");
    } catch {
      setResetFormError("Şifre sıfırlanamadı.");
    } finally {
      setResetting(false);
    }
  }

  function openHardDeleteDialog(user: StaffUser) {
    setHardDeleteAcknowledged(false);
    setHardDeleteTarget(user);
  }

  async function handleConfirmHardDelete() {
    if (!hardDeleteTarget || !hardDeleteAcknowledged) return;
    setHardDeleting(true);
    try {
      await hardDeletePlatformStaffUser(businessId, hardDeleteTarget.id);
      setHardDeleteTarget(null);
      load();
      showToast("Kullanıcı kalıcı olarak silindi.", "success");
    } catch {
      showToast("Kullanıcı silinemedi.", "error");
    } finally {
      setHardDeleting(false);
    }
  }

  if (loading) {
    return (
      <AppShell>
        <main className={styles.page}>
          <TableSkeleton />
        </main>
      </AppShell>
    );
  }

  if (error || !business) {
    return (
      <AppShell>
        <main className={styles.page}>
          <ErrorState message={error ?? "İşletme bulunamadı."} onRetry={load} />
        </main>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <div className={pageStyles.backLinkRow}>
          <Link href="/platform-admin/businesses" className={styles.backLink}>
            <ArrowLeft size={14} aria-hidden="true" style={{ verticalAlign: "-2px" }} /> İşletmeler
          </Link>
        </div>

        <PageHeader
          title={business.name}
          description={`${business.defaultCurrency} · ${business.defaultTimeZone}`}
          actions={
            <>
              <Button variant="ghost" onClick={openEditBusinessNameDialog}>
                <Pencil size={15} aria-hidden="true" /> Düzenle
              </Button>
              <Button variant={business.active ? "secondary" : "primary"} disabled={togglingBusiness} onClick={handleToggleBusinessActive}>
                {business.active ? "Pasifleştir" : "Aktifleştir"}
              </Button>
            </>
          }
        />

        <section className={`${styles.section} ${styles.panel}`}>
          <div className={pageStyles.summaryGrid}>
            <div>
              <div className={pageStyles.summaryLabel}>Durum</div>
              <div className={pageStyles.summaryValue}>
                <Badge tone={business.active ? "success" : "danger"}>{business.active ? "Aktif" : "Pasif"}</Badge>
              </div>
            </div>
            <div>
              <div className={pageStyles.summaryLabel}>Şube sayısı</div>
              <div className={pageStyles.summaryValue}>{branches.length}</div>
            </div>
            <div>
              <div className={pageStyles.summaryLabel}>Kullanıcı sayısı</div>
              <div className={pageStyles.summaryValue}>{staffUsers.length}</div>
            </div>
          </div>
        </section>

        <section className={`${styles.section} ${styles.panel} ${pageStyles.tablePanel}`}>
          <div className={pageStyles.panelHeader} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "1rem" }}>
            <div>
              <h2 className={styles.sectionTitle}>Şubeler</h2>
              <p className={styles.rowMeta}>{branches.length} şube</p>
            </div>
            <Button
              size="md"
              variant="secondary"
              onClick={() => {
                setBranchFormError(null);
                setBranchName("");
                setBranchDialogOpen(true);
              }}
            >
              <Plus size={16} aria-hidden="true" /> Şube Ekle
            </Button>
          </div>
          <div className={pageStyles.panelBody}>
            {branches.length === 0 ? (
              <EmptyState icon={<Store size={20} />} title="Henüz şube yok" description="İlk şubeyi ekleyin." />
            ) : (
              <Table>
                <thead>
                  <tr>
                    <th>Şube</th>
                    <th>Durum</th>
                    <th>Sipariş alımı</th>
                    <th>Adres</th>
                    <th className={pageStyles.actionsHeader}>İşlemler</th>
                  </tr>
                </thead>
                <tbody>
                  {branches.map((branch) => (
                    <tr key={branch.id}>
                      <td className={tableStyles.primary}>{branch.name}</td>
                      <td>
                        <Badge tone={branch.active ? "success" : "danger"}>{branch.active ? "Aktif" : "Pasif"}</Badge>
                      </td>
                      <td>
                        <Badge tone={branch.orderingEnabled ? "success" : "neutral"}>{branch.orderingEnabled ? "Açık" : "Kapalı"}</Badge>
                      </td>
                      <td>{branch.address ?? "—"}</td>
                      <td className={pageStyles.actionsCell}>
                        <div className={`${tableStyles.actions} ${pageStyles.rowActionsInline}`}>
                          <Button size="md" variant="ghost" onClick={() => openEditBranchDialog(branch)}>
                            <Pencil size={15} aria-hidden="true" /> Düzenle
                          </Button>
                          <Button
                            className={branch.active ? pageStyles.dangerAction : undefined}
                            size="md"
                            variant="ghost"
                            disabled={togglingBranchId === branch.id}
                            onClick={() => handleToggleBranchActive(branch)}
                          >
                            {branch.active ? "Pasifleştir" : "Aktifleştir"}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
          </div>
        </section>

        <section className={`${styles.section} ${styles.panel} ${pageStyles.tablePanel}`}>
          <div className={pageStyles.panelHeader} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "1rem" }}>
            <div>
              <h2 className={styles.sectionTitle}>Kullanıcılar</h2>
              <p className={styles.rowMeta}>{staffUsers.length} kullanıcı</p>
            </div>
            <Button size="md" onClick={openStaffDialog} disabled={branches.length === 0}>
              <UserPlus size={16} aria-hidden="true" /> Kullanıcı Ekle
            </Button>
          </div>
          <div className={pageStyles.panelBody}>
            {branches.length === 0 ? (
              <EmptyState icon={<Users size={20} />} title="Önce bir şube ekleyin" description="Kullanıcı oluşturmak için işletmenin en az bir şubesi olmalı." />
            ) : staffUsers.length === 0 ? (
              <EmptyState icon={<Users size={20} />} title="Henüz kullanıcı yok" description="İlk kullanıcıyı ekleyin." />
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
                        {user.role === "PLATFORM_ADMIN" ? (
                          <span className={styles.rowMeta}>Bu panelden yönetilemez</span>
                        ) : (
                          <div className={`${tableStyles.actions} ${pageStyles.rowActionsInline}`}>
                            <Button size="md" variant="ghost" onClick={() => openRoleDialog(user)}>
                              Rol Değiştir
                            </Button>
                            <Button size="md" variant="ghost" onClick={() => openResetDialog(user)}>
                              <KeyRound size={15} aria-hidden="true" /> Şifre Sıfırla
                            </Button>
                            <Button
                              className={user.active ? pageStyles.dangerAction : undefined}
                              size="md"
                              variant="ghost"
                              disabled={togglingStaffId === user.id}
                              onClick={() => handleToggleStaffActive(user)}
                            >
                              {user.active ? "Devre Dışı Bırak" : "Aktifleştir"}
                            </Button>
                            <Button
                              className={pageStyles.dangerAction}
                              size="md"
                              variant="ghost"
                              disabled={user.id === currentStaffUserId}
                              onClick={() => openHardDeleteDialog(user)}
                            >
                              <Trash2 size={15} aria-hidden="true" /> Kalıcı Olarak Sil
                            </Button>
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

      {editingBusinessName ? (
        <Dialog onClose={() => setEditingBusinessName(false)} labelledBy={businessNameDialogTitleId}>
          <h2 id={businessNameDialogTitleId} className={styles.sectionTitle}>
            İşletme Adını Düzenle
          </h2>
          <form className={styles.section} onSubmit={handleSaveBusinessName}>
            <FormField label="İşletme Adı" required>
              {(controlProps) => (
                <Input {...controlProps} value={businessNameInput} onChange={(event) => setBusinessNameInput(event.target.value)} required />
              )}
            </FormField>

            {businessNameFormError ? <ErrorState message={businessNameFormError} /> : null}

            <Button type="submit" disabled={savingBusinessName}>
              {savingBusinessName ? "Kaydediliyor…" : "Kaydet"}
            </Button>
          </form>
        </Dialog>
      ) : null}

      {branchDialogOpen ? (
        <Dialog onClose={() => setBranchDialogOpen(false)} labelledBy={branchDialogTitleId}>
          <h2 id={branchDialogTitleId} className={styles.sectionTitle}>
            Yeni Şube
          </h2>
          <form className={styles.section} onSubmit={handleCreateBranch}>
            <FormField label="Şube Adı" required>
              {(controlProps) => <Input {...controlProps} value={branchName} onChange={(event) => setBranchName(event.target.value)} required />}
            </FormField>

            {branchFormError ? <ErrorState message={branchFormError} /> : null}

            <Button type="submit" disabled={creatingBranch}>
              {creatingBranch ? "Oluşturuluyor…" : "Şube Ekle"}
            </Button>
          </form>
        </Dialog>
      ) : null}

      {editingBranch ? (
        <Dialog onClose={() => setEditingBranch(null)} labelledBy={branchEditDialogTitleId}>
          <h2 id={branchEditDialogTitleId} className={styles.sectionTitle}>
            Şubeyi Düzenle
          </h2>
          <form className={styles.section} onSubmit={handleSaveBranchEdit}>
            <FormField label="Şube Adı" required>
              {(controlProps) => (
                <Input {...controlProps} value={editBranchName} onChange={(event) => setEditBranchName(event.target.value)} required />
              )}
            </FormField>
            <FormField label="Adres">
              {(controlProps) => (
                <Input {...controlProps} value={editBranchAddress} onChange={(event) => setEditBranchAddress(event.target.value)} />
              )}
            </FormField>

            {editBranchFormError ? <ErrorState message={editBranchFormError} /> : null}

            <Button type="submit" disabled={savingBranchEdit}>
              {savingBranchEdit ? "Kaydediliyor…" : "Kaydet"}
            </Button>
          </form>
        </Dialog>
      ) : null}

      {staffDialogOpen ? (
        <Dialog onClose={() => setStaffDialogOpen(false)} labelledBy={staffDialogTitleId}>
          <h2 id={staffDialogTitleId} className={styles.sectionTitle}>
            Yeni Kullanıcı
          </h2>
          <form className={styles.section} onSubmit={handleCreateStaff}>
            <FormField label="E-posta" required>
              {(controlProps) => <Input {...controlProps} type="email" value={staffEmail} onChange={(event) => setStaffEmail(event.target.value)} required />}
            </FormField>
            <FormField label="Şifre" hint={`En az ${MIN_PASSWORD_LENGTH} karakter`} required>
              {(controlProps) => (
                <div className={pageStyles.passwordField}>
                  <Input
                    {...controlProps}
                    type={staffPasswordVisible ? "text" : "password"}
                    className={pageStyles.passwordInput}
                    value={staffPassword}
                    onChange={(event) => setStaffPassword(event.target.value)}
                    autoComplete="new-password"
                    required
                  />
                  <button
                    type="button"
                    className={pageStyles.passwordToggle}
                    onClick={() => setStaffPasswordVisible((visible) => !visible)}
                    aria-label={staffPasswordVisible ? "Şifreyi gizle" : "Şifreyi göster"}
                    aria-pressed={staffPasswordVisible}
                    disabled={creatingStaff}
                  >
                    {staffPasswordVisible ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
                  </button>
                </div>
              )}
            </FormField>
            <FormField label="Rol">
              {(controlProps) => (
                <Select {...controlProps} value={staffRole} onChange={(event) => setStaffRole(event.target.value as StaffRole)}>
                  <option value="BUSINESS_ADMIN">İşletme Yöneticisi</option>
                  <option value="BRANCH_MANAGER">Şube Sorumlusu</option>
                  <option value="CASHIER">Kasa Personeli</option>
                </Select>
              )}
            </FormField>
            <FormField label="Şube" required>
              {(controlProps) => (
                <Select {...controlProps} value={staffBranchId} onChange={(event) => setStaffBranchId(event.target.value)} required>
                  {branches.map((branch) => (
                    <option key={branch.id} value={branch.id}>
                      {branch.name}
                    </option>
                  ))}
                </Select>
              )}
            </FormField>

            {staffFormError ? <ErrorState message={staffFormError} /> : null}

            <Button type="submit" disabled={creatingStaff}>
              {creatingStaff ? "Oluşturuluyor…" : "Kullanıcı Ekle"}
            </Button>
          </form>
        </Dialog>
      ) : null}

      {roleTarget ? (
        <Dialog onClose={() => setRoleTarget(null)} labelledBy={roleDialogTitleId}>
          <h2 id={roleDialogTitleId} className={styles.sectionTitle}>
            Rol Değiştir
          </h2>
          <form className={styles.section} onSubmit={handleChangeRole}>
            <p className={styles.rowMeta}>{`"${roleTarget.email}" için yeni rol seçin.`}</p>
            <FormField label="Rol">
              {(controlProps) => (
                <Select {...controlProps} value={roleTargetRole} onChange={(event) => setRoleTargetRole(event.target.value as StaffRole)}>
                  <option value="BUSINESS_ADMIN">İşletme Yöneticisi</option>
                  <option value="BRANCH_MANAGER">Şube Sorumlusu</option>
                  <option value="CASHIER">Kasa Personeli</option>
                </Select>
              )}
            </FormField>
            <Button type="submit" disabled={changingRole}>
              {changingRole ? "Kaydediliyor…" : "Rolü Kaydet"}
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
              {`"${resetTarget.email}" için yeni bir geçici şifre belirleyin. Bu şifreyi kullanıcıyla güvenli bir şekilde paylaşın.`}
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

      {hardDeleteTarget ? (
        <Dialog onClose={() => setHardDeleteTarget(null)} labelledBy={hardDeleteDialogTitleId}>
          <h2 id={hardDeleteDialogTitleId} className={styles.sectionTitle}>
            Kullanıcıyı Kalıcı Olarak Sil
          </h2>
          <div className={styles.section}>
            <p className={styles.rowMeta}>
              {`"${hardDeleteTarget.email}" kullanıcısı kalıcı olarak silinecek, geri alınamaz. Geçmiş sipariş/gider/denetim kayıtları korunur ancak bu kullanıcıya olan referansları kaldırılır.`}
            </p>
            <label className={pageStyles.checkboxRow}>
              <input
                type="checkbox"
                checked={hardDeleteAcknowledged}
                onChange={(event) => setHardDeleteAcknowledged(event.target.checked)}
              />
              Bu işlemin geri alınamaz olduğunu anlıyorum
            </label>
            <Button variant="danger" disabled={!hardDeleteAcknowledged || hardDeleting} onClick={handleConfirmHardDelete}>
              {hardDeleting ? "Siliniyor…" : "Kalıcı Olarak Sil"}
            </Button>
          </div>
        </Dialog>
      ) : null}
    </AppShell>
  );
}
