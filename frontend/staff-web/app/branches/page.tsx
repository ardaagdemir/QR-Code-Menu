"use client";

import { useEffect, useId, useState } from "react";
import Link from "next/link";
import { ApiError, createBranch, listBranches, setDeliveryModel, setOrderingEnabled, type Branch, type DeliveryModel } from "@/lib/api";
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

/** Section 4, staff-web admin screen: Branch management (Permission.BRANCH_MANAGE). */
export default function BranchesPage() {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [branches, setBranches] = useState<Branch[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState("");
  const [deliveryModel, setDeliveryModelInput] = useState<DeliveryModel>("WAITER_DELIVERY");
  const [creating, setCreating] = useState(false);

  function load() {
    listBranches()
      .then((data) => {
        setBranches(data);
        setError(null);
      })
      .catch((err) => setError(err instanceof ApiError ? "Şubeler yüklenemedi." : "Beklenmedik bir hata oluştu."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    if (!name.trim()) {
      return;
    }
    setCreating(true);
    try {
      await createBranch(name.trim(), deliveryModel);
      setName("");
      setDeliveryModelInput("WAITER_DELIVERY");
      setCreateOpen(false);
      load();
      showToast("Şube oluşturuldu.", "success");
    } catch {
      showToast("Şube oluşturulamadı.", "error");
    } finally {
      setCreating(false);
    }
  }

  async function handleToggleDeliveryModel(branch: Branch) {
    setTogglingId(branch.id);
    try {
      const nextModel = branch.deliveryModel === "CUSTOMER_PICKUP" ? "WAITER_DELIVERY" : "CUSTOMER_PICKUP";
      const updated = await setDeliveryModel(branch.id, nextModel);
      setBranches((current) => current.map((b) => (b.id === updated.id ? updated : b)));
    } catch {
      showToast("Teslimat modeli güncellenemedi.", "error");
    } finally {
      setTogglingId(null);
    }
  }

  async function handleToggleOrdering(branch: Branch) {
    setTogglingId(branch.id);
    try {
      const updated = await setOrderingEnabled(branch.id, !branch.orderingEnabled);
      setBranches((current) => current.map((b) => (b.id === updated.id ? updated : b)));
    } catch {
      showToast("Sipariş durumu güncellenemedi.", "error");
    } finally {
      setTogglingId(null);
    }
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader
          title="Şubeler"
          description="İşletmenin şubeleri, teslimat modeli ve sipariş durumu."
          actions={<Button onClick={() => setCreateOpen(true)}>+ Şube Ekle</Button>}
        />

        {loading ? (
          <TableSkeleton />
        ) : error ? (
          <ErrorState message={error} onRetry={load} />
        ) : branches.length === 0 ? (
          <EmptyState title="Henüz şube yok" description="Başlamak için bir şube ekleyin." />
        ) : (
          <Table>
            <thead>
              <tr>
                <th>Şube</th>
                <th>Sipariş</th>
                <th>Teslimat modeli</th>
                <th>Kısayollar</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {branches.map((branch) => (
                <tr key={branch.id}>
                  <td>
                    <Link href={`/branches/${branch.id}`} className={tableStyles.primary}>
                      {branch.name}
                    </Link>
                  </td>
                  <td>
                    <Badge tone={branch.orderingEnabled ? "neutral" : "danger"}>
                      {branch.orderingEnabled ? "Açık" : "Kapalı"}
                    </Badge>
                  </td>
                  <td>{branch.deliveryModel === "CUSTOMER_PICKUP" ? "Pickup" : "Garson servisi"}</td>
                  <td>
                    <div className={styles.rowActions}>
                      <Link href={`/cashier/${branch.id}`} className={styles.backLink}>
                        Kasa
                      </Link>
                      <Link href={`/refunds/${branch.id}`} className={styles.backLink}>
                        İadeler
                      </Link>
                      <Link href={`/reports/${branch.id}`} className={styles.backLink}>
                        Raporlar
                      </Link>
                      {branch.deliveryModel === "CUSTOMER_PICKUP" ? (
                        <Link href={`/pickup/${branch.id}`} className={styles.backLink} target="_blank">
                          Pickup Board
                        </Link>
                      ) : null}
                    </div>
                  </td>
                  <td>
                    <div className={tableStyles.actions}>
                      <Button size="md" variant="secondary" disabled={togglingId === branch.id} onClick={() => handleToggleOrdering(branch)}>
                        {branch.orderingEnabled ? "Siparişi Kapat" : "Siparişi Aç"}
                      </Button>
                      <Button size="md" variant="ghost" disabled={togglingId === branch.id} onClick={() => handleToggleDeliveryModel(branch)}>
                        {branch.deliveryModel === "CUSTOMER_PICKUP" ? "Garson servisine geç" : "Pickup'a geç"}
                      </Button>
                    </div>
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
            Yeni Şube
          </h2>
          <form className={styles.section} onSubmit={handleCreate}>
            <FormField label="Şube adı" required>
              {(controlProps) => <Input {...controlProps} value={name} onChange={(event) => setName(event.target.value)} required />}
            </FormField>
            <FormField label="Teslimat modeli">
              {(controlProps) => (
                <Select
                  {...controlProps}
                  value={deliveryModel}
                  onChange={(event) => setDeliveryModelInput(event.target.value as DeliveryModel)}
                >
                  <option value="WAITER_DELIVERY">Garson servisi</option>
                  <option value="CUSTOMER_PICKUP">Müşteri kendi alır (pickup)</option>
                </Select>
              )}
            </FormField>
            <Button type="submit" disabled={creating}>
              {creating ? "Oluşturuluyor…" : "Şube Ekle"}
            </Button>
          </form>
        </Dialog>
      ) : null}
    </AppShell>
  );
}
