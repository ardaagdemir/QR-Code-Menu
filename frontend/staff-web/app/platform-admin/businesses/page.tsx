"use client";

import { useEffect, useId, useState } from "react";
import Link from "next/link";
import { Building2, ChevronRight, Plus } from "lucide-react";
import {
  activatePlatformBusiness,
  createPlatformBusiness,
  deactivatePlatformBusiness,
  listPlatformBusinesses,
  type Business,
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
import pageStyles from "../platform-admin.module.css";

/** Platform Admin Panel - işletme listesi/oluşturma. PLATFORM_ADMIN only, /api/platform-admin/**
 * üzerinden - kendi StaffUser.businessId'sinden bağımsız, tüm işletmeleri gösterir. */
export default function PlatformAdminBusinessesPage() {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [businesses, setBusinesses] = useState<Business[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState("");
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const [togglingId, setTogglingId] = useState<string | null>(null);

  function load() {
    listPlatformBusinesses()
      .then((data) => {
        setBusinesses(data);
        setError(null);
      })
      .catch(() => setError("İşletme listesi yüklenemedi."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    if (!name.trim()) {
      setFormError("İşletme adı girin.");
      return;
    }
    setCreating(true);
    setFormError(null);
    try {
      await createPlatformBusiness(name.trim());
      setName("");
      setCreateOpen(false);
      load();
      showToast("İşletme oluşturuldu.", "success");
    } catch {
      setFormError("İşletme oluşturulamadı.");
    } finally {
      setCreating(false);
    }
  }

  async function handleToggleActive(business: Business) {
    setTogglingId(business.id);
    try {
      const updated = business.active ? await deactivatePlatformBusiness(business.id) : await activatePlatformBusiness(business.id);
      setBusinesses((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      showToast(updated.active ? "İşletme aktifleştirildi." : "İşletme pasifleştirildi.", "success");
    } catch {
      showToast("İşletme durumu güncellenemedi.", "error");
    } finally {
      setTogglingId(null);
    }
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader
          title="Platform Admin · İşletmeler"
          description="Tüm işletmeleri oluşturun, şubelerini ve kullanıcılarını yönetin."
          actions={
            <Button
              onClick={() => {
                setFormError(null);
                setName("");
                setCreateOpen(true);
              }}
            >
              <Plus size={17} aria-hidden="true" /> Yeni İşletme
            </Button>
          }
        />

        <section className={`${styles.section} ${styles.panel} ${pageStyles.tablePanel}`}>
          <div className={pageStyles.panelHeader}>
            <h2 className={styles.sectionTitle}>İşletme Listesi</h2>
            <p className={styles.rowMeta}>{businesses.length} işletme</p>
          </div>
          <div className={pageStyles.panelBody}>
            {loading ? (
              <TableSkeleton />
            ) : error ? (
              <ErrorState message={error} onRetry={load} />
            ) : businesses.length === 0 ? (
              <EmptyState icon={<Building2 size={20} />} title="Henüz işletme yok" description="İlk işletmeyi oluşturun." />
            ) : (
              <Table>
                <thead>
                  <tr>
                    <th>İşletme</th>
                    <th>Durum</th>
                    <th>Para birimi</th>
                    <th className={pageStyles.actionsHeader}>İşlemler</th>
                  </tr>
                </thead>
                <tbody>
                  {businesses.map((business) => (
                    <tr key={business.id}>
                      <td>
                        <Link href={`/platform-admin/businesses/${business.id}`} className={pageStyles.identity}>
                          <span className={pageStyles.avatar} aria-hidden="true">
                            {business.name.slice(0, 2).toUpperCase()}
                          </span>
                          <span className={tableStyles.primary}>{business.name}</span>
                        </Link>
                      </td>
                      <td>
                        <Badge tone={business.active ? "success" : "danger"}>{business.active ? "Aktif" : "Pasif"}</Badge>
                      </td>
                      <td>{business.defaultCurrency}</td>
                      <td className={pageStyles.actionsCell}>
                        <div className={`${tableStyles.actions} ${pageStyles.rowActionsInline}`}>
                          <Button size="md" variant="ghost" disabled={togglingId === business.id} onClick={() => handleToggleActive(business)}>
                            {business.active ? "Pasifleştir" : "Aktifleştir"}
                          </Button>
                          <Link href={`/platform-admin/businesses/${business.id}`} className={pageStyles.detailLink}>
                            Detay <ChevronRight size={15} aria-hidden="true" />
                          </Link>
                        </div>
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
            Yeni İşletme
          </h2>
          <form className={styles.section} onSubmit={handleCreate}>
            <FormField label="İşletme Adı" required>
              {(controlProps) => <Input {...controlProps} value={name} onChange={(event) => setName(event.target.value)} required />}
            </FormField>

            {formError ? <ErrorState message={formError} /> : null}

            <Button type="submit" disabled={creating}>
              {creating ? "Oluşturuluyor…" : "İşletme Oluştur"}
            </Button>
          </form>
        </Dialog>
      ) : null}
    </AppShell>
  );
}
