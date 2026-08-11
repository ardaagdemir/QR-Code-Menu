"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ApiError, createBranch, listBranches, setDeliveryModel, setOrderingEnabled, type Branch, type DeliveryModel } from "@/lib/api";
import StaffNav from "@/components/layout/StaffNav";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import styles from "@/styles/admin.module.css";

/** Section 4, staff-web admin screen: Branch management (Permission.BRANCH_MANAGE). */
export default function BranchesPage() {
  const [branches, setBranches] = useState<Branch[]>([]);
  const [loading, setLoading] = useState(true);
  const [name, setName] = useState("");
  const [deliveryModel, setDeliveryModelInput] = useState<DeliveryModel>("WAITER_DELIVERY");
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  async function reload() {
    try {
      setBranches(await listBranches());
    } catch (err) {
      setError(err instanceof ApiError ? "Şubeler yüklenemedi." : "Beklenmedik bir hata oluştu.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    listBranches()
      .then((data) => {
        if (!cancelled) {
          setBranches(data);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setError("Şubeler yüklenemedi.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    if (!name.trim()) {
      return;
    }
    setCreating(true);
    setError(null);
    try {
      await createBranch(name.trim(), deliveryModel);
      setName("");
      await reload();
    } catch {
      setError("Şube oluşturulamadı.");
    } finally {
      setCreating(false);
    }
  }

  async function handleToggleDeliveryModel(branch: Branch) {
    setTogglingId(branch.id);
    setError(null);
    try {
      const nextModel = branch.deliveryModel === "CUSTOMER_PICKUP" ? "WAITER_DELIVERY" : "CUSTOMER_PICKUP";
      const updated = await setDeliveryModel(branch.id, nextModel);
      setBranches((current) => current.map((b) => (b.id === updated.id ? updated : b)));
    } catch {
      setError("Teslimat modeli güncellenemedi.");
    } finally {
      setTogglingId(null);
    }
  }

  async function handleToggleOrdering(branch: Branch) {
    setTogglingId(branch.id);
    setError(null);
    try {
      const updated = await setOrderingEnabled(branch.id, !branch.orderingEnabled);
      setBranches((current) => current.map((b) => (b.id === updated.id ? updated : b)));
    } catch {
      setError("Sipariş durumu güncellenemedi.");
    } finally {
      setTogglingId(null);
    }
  }

  return (
    <>
      <StaffNav />
      <main className={styles.page}>
        <div className={styles.header}>
          <h1 className={styles.title}>Şubeler</h1>
        </div>

        <form className={styles.form} onSubmit={handleCreate}>
          <div className={styles.field}>
            <label className={styles.label} htmlFor="branch-name">
              Yeni şube adı
            </label>
            <input id="branch-name" className={styles.input} value={name} onChange={(event) => setName(event.target.value)} required />
          </div>
          <div className={styles.field}>
            <label className={styles.label} htmlFor="branch-delivery-model">
              Teslimat modeli
            </label>
            <select
              id="branch-delivery-model"
              className={styles.select}
              value={deliveryModel}
              onChange={(event) => setDeliveryModelInput(event.target.value as DeliveryModel)}
            >
              <option value="WAITER_DELIVERY">Garson servisi</option>
              <option value="CUSTOMER_PICKUP">Müşteri kendi alır (pickup)</option>
            </select>
          </div>
          <Button type="submit" disabled={creating}>
            {creating ? "Oluşturuluyor…" : "Şube Ekle"}
          </Button>
        </form>

        {error ? <p className={styles.error}>{error}</p> : null}

        <div className={styles.list}>
          {loading ? (
            <p className={styles.empty}>Yükleniyor…</p>
          ) : branches.length === 0 ? (
            <p className={styles.empty}>Henüz şube yok.</p>
          ) : (
            branches.map((branch) => (
              <div key={branch.id} className={styles.row}>
                <div className={styles.rowMain}>
                  <Link href={`/branches/${branch.id}`} className={styles.rowTitle}>
                    {branch.name}
                  </Link>
                  <span className={styles.rowMeta}>
                    <Badge tone={branch.orderingEnabled ? "neutral" : "danger"}>
                      {branch.orderingEnabled ? "Sipariş açık" : "Sipariş kapalı"}
                    </Badge>{" "}
                    <Badge tone="neutral">
                      {branch.deliveryModel === "CUSTOMER_PICKUP" ? "Pickup" : "Garson servisi"}
                    </Badge>
                  </span>
                </div>
                <div className={styles.rowActions}>
                  <Button size="md" variant="secondary" disabled={togglingId === branch.id} onClick={() => handleToggleOrdering(branch)}>
                    {branch.orderingEnabled ? "Siparişi Kapat" : "Siparişi Aç"}
                  </Button>
                  <Button size="md" variant="ghost" disabled={togglingId === branch.id} onClick={() => handleToggleDeliveryModel(branch)}>
                    {branch.deliveryModel === "CUSTOMER_PICKUP" ? "Garson servisine geç" : "Pickup'a geç"}
                  </Button>
                  <Link href={`/kitchen/${branch.id}`} className={styles.backLink}>
                    Mutfak
                  </Link>
                  <Link href={`/refunds/${branch.id}`} className={styles.backLink}>
                    İadeler
                  </Link>
                  {branch.deliveryModel === "CUSTOMER_PICKUP" ? (
                    <Link href={`/pickup/${branch.id}`} className={styles.backLink} target="_blank">
                      Pickup Board
                    </Link>
                  ) : null}
                </div>
              </div>
            ))
          )}
        </div>
      </main>
    </>
  );
}
