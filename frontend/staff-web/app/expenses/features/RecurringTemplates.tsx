"use client";

import { useEffect, useId, useState } from "react";
import {
  createRecurringExpenseTemplate,
  deactivateRecurringExpenseTemplate,
  formatPriceMinorUnits,
  listRecurringExpenseTemplates,
  type StaffBranchSummary,
  type ExpenseCategory,
  type RecurringExpenseTemplate,
} from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import { useToast } from "@/components/ui/ToastProvider";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

type Props = {
  categories: ExpenseCategory[];
  accessibleBranches: StaffBranchSummary[];
};

/** Gider Yönetimi'nin tekrarlayan gider şablonu paneli (product-requirements.md Section 16). */
export default function RecurringTemplates({ categories, accessibleBranches }: Props) {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [templates, setTemplates] = useState<RecurringExpenseTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [categoryId, setCategoryId] = useState("");
  const [amount, setAmount] = useState("");
  const [dayOfMonth, setDayOfMonth] = useState("1");
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<RecurringExpenseTemplate | null>(null);
  const [deactivating, setDeactivating] = useState(false);

  const activeCategories = categories.filter((category) => category.active);

  function load() {
    listRecurringExpenseTemplates()
      .then((data) => {
        setTemplates(data);
        setError(null);
      })
      .catch(() => setError("Şablonlar yüklenemedi."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleCreateTemplate(event: React.FormEvent) {
    event.preventDefault();
    const amountMinorUnits = Math.round(Number(amount.replace(",", ".")) * 100);
    const day = Number(dayOfMonth);
    if (!categoryId || !Number.isFinite(amountMinorUnits) || amountMinorUnits <= 0 || day < 1 || day > 31) {
      setFormError("Kategori, tutar ve geçerli bir gün (1-31) girin.");
      return;
    }
    setCreating(true);
    setFormError(null);
    try {
      await createRecurringExpenseTemplate({
        branchId: null,
        categoryId,
        amountMinorUnits,
        vendor: null,
        description: null,
        dayOfMonth: day,
        startDate: todayIsoDate(),
        endDate: null,
      });
      setAmount("");
      setCreateOpen(false);
      load();
      showToast("Şablon oluşturuldu.", "success");
    } catch {
      setFormError("Tekrarlayan gider şablonu oluşturulamadı.");
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
      await deactivateRecurringExpenseTemplate(deactivateTarget.id);
      load();
      showToast("Şablon devre dışı bırakıldı.", "success");
    } catch {
      showToast("Şablon devre dışı bırakılamadı.", "error");
    } finally {
      setDeactivating(false);
      setDeactivateTarget(null);
    }
  }

  function branchName(branchId: string | null): string {
    if (!branchId) return "İşletme geneli";
    return accessibleBranches.find((b) => b.id === branchId)?.name ?? branchId;
  }

  const activeTemplates = templates.filter((t) => t.active);

  return (
    <section className={styles.section}>
      <PageHeader title="Tekrarlayan Gider Şablonları" actions={<Button onClick={() => setCreateOpen(true)}>+ Şablon Ekle</Button>} />

      {loading ? (
        <TableSkeleton />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : activeTemplates.length === 0 ? (
        <EmptyState title="Aktif şablon yok" />
      ) : (
        <Table>
          <thead>
            <tr>
              <th>Kategori / Tutar</th>
              <th>Ayın günü</th>
              <th>Şube</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {activeTemplates.map((template) => (
              <tr key={template.id}>
                <td className={tableStyles.primary}>
                  {template.categoryName ?? "—"} · {formatPriceMinorUnits(template.amountMinorUnits)}
                </td>
                <td className={tableStyles.muted}>Her ayın {template.dayOfMonth}. günü</td>
                <td className={tableStyles.muted}>{branchName(template.branchId)}</td>
                <td>
                  <div className={tableStyles.actions}>
                    <Button size="md" variant="ghost" onClick={() => setDeactivateTarget(template)}>
                      Devre Dışı Bırak
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      {createOpen ? (
        <Dialog onClose={() => setCreateOpen(false)} labelledBy={dialogTitleId}>
          <h2 id={dialogTitleId} className={styles.sectionTitle}>
            Yeni Şablon
          </h2>
          <form className={styles.section} onSubmit={handleCreateTemplate}>
            <FormField label="Kategori" required>
              {(controlProps) => (
                <Select {...controlProps} value={categoryId} onChange={(event) => setCategoryId(event.target.value)} required>
                  <option value="">Seçin</option>
                  {activeCategories.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </Select>
              )}
            </FormField>
            <FormField label="Tutar (₺)" required>
              {(controlProps) => <Input {...controlProps} value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="0,00" required />}
            </FormField>
            <FormField label="Ayın günü" required>
              {(controlProps) => (
                <Input {...controlProps} type="number" min={1} max={31} value={dayOfMonth} onChange={(event) => setDayOfMonth(event.target.value)} required />
              )}
            </FormField>

            {formError ? <ErrorState message={formError} /> : null}

            <Button type="submit" disabled={creating}>
              {creating ? "Oluşturuluyor…" : "Şablon Ekle"}
            </Button>
          </form>
        </Dialog>
      ) : null}

      {deactivateTarget ? (
        <ConfirmDialog
          title="Şablonu Devre Dışı Bırak"
          message={`Bu tekrarlayan gider şablonu devre dışı bırakılacak ve artık otomatik gider oluşturmayacak. Bu işlem geri alınamaz.`}
          confirmLabel="Devre Dışı Bırak"
          tone="danger"
          confirmLoading={deactivating}
          onConfirm={handleConfirmDeactivate}
          onCancel={() => setDeactivateTarget(null)}
        />
      ) : null}
    </section>
  );
}
