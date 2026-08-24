"use client";

import { useEffect, useId, useState } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import {
  activateRecurringExpenseTemplate,
  createRecurringExpenseTemplate,
  deactivateRecurringExpenseTemplate,
  deleteRecurringExpenseTemplate,
  formatPriceMinorUnits,
  listRecurringExpenseTemplates,
  updateRecurringExpenseTemplate,
  type ExpenseCategory,
  type RecurringExpenseTemplate,
} from "@/lib/api";
import { branchIsoDate } from "@/lib/time";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import { useToast } from "@/components/ui/ToastProvider";
import { formatAmountInput, formatAmountMinorUnitsForInput, parseAmountInputToMinorUnits } from "./expenseAmount";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";
import expenseStyles from "../expenses.module.css";

const trDateFormatter = new Intl.DateTimeFormat("tr-TR", {
  day: "numeric",
  month: "short",
  year: "numeric",
  timeZone: "UTC",
});

function formatIsoDate(value: string): string {
  return trDateFormatter.format(new Date(`${value}T00:00:00Z`));
}

function dateParts(value: string): [number, number, number] {
  const [year, month, day] = value.split("-").map(Number);
  return [year, month, day];
}

function toIsoDate(year: number, monthIndex: number, dayOfMonth: number): string {
  const lastDay = new Date(Date.UTC(year, monthIndex + 1, 0)).getUTCDate();
  return new Date(Date.UTC(year, monthIndex, Math.min(dayOfMonth, lastDay))).toISOString().slice(0, 10);
}

function nextDueDate(template: RecurringExpenseTemplate, today: string): string | null {
  if (!template.active) {
    return null;
  }

  const [todayYear, todayMonth] = dateParts(today);
  const [startYear, startMonth] = dateParts(template.startDate);
  let year = todayYear;
  let monthIndex = todayMonth - 1;

  if (startYear > year || (startYear === year && startMonth - 1 > monthIndex)) {
    year = startYear;
    monthIndex = startMonth - 1;
  }

  for (let offset = 0; offset < 24; offset += 1) {
    const candidate = toIsoDate(year, monthIndex, template.dayOfMonth);
    if (candidate > today && candidate >= template.startDate && (!template.endDate || candidate <= template.endDate)) {
      return candidate;
    }
    monthIndex += 1;
    if (monthIndex === 12) {
      monthIndex = 0;
      year += 1;
    }
  }
  return null;
}

function templateName(template: RecurringExpenseTemplate): string {
  return template.description?.trim() || template.vendor?.trim() || template.categoryName || "Gider";
}

type Props = {
  categories: ExpenseCategory[];
  branchTimeZone: string | null;
};

/** Gider Yönetimi'nin tekrarlayan gider şablonu paneli (product-requirements.md Section 16). */
export default function RecurringTemplates({ categories, branchTimeZone }: Props) {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [templates, setTemplates] = useState<RecurringExpenseTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<RecurringExpenseTemplate | null>(null);
  const [name, setName] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [amount, setAmount] = useState("");
  const [dayOfMonth, setDayOfMonth] = useState("1");
  const [startDate, setStartDate] = useState(branchIsoDate(branchTimeZone));
  const [endDate, setEndDate] = useState("");
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const [deactivateTarget, setDeactivateTarget] = useState<RecurringExpenseTemplate | null>(null);
  const [deactivating, setDeactivating] = useState(false);
  const [activatingTemplateId, setActivatingTemplateId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<RecurringExpenseTemplate | null>(null);
  const [deleting, setDeleting] = useState(false);

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

  function openCreateDialog() {
    setEditingTemplate(null);
    setName("");
    setCategoryId("");
    setAmount("");
    setDayOfMonth("1");
    setStartDate(branchIsoDate(branchTimeZone));
    setEndDate("");
    setFormError(null);
    setFormOpen(true);
  }

  function openEditDialog(template: RecurringExpenseTemplate) {
    setEditingTemplate(template);
    setName(templateName(template));
    setCategoryId(template.categoryId);
    setAmount(formatAmountMinorUnitsForInput(template.amountMinorUnits));
    setDayOfMonth(String(template.dayOfMonth));
    setStartDate(template.startDate);
    setEndDate(template.endDate ?? "");
    setFormError(null);
    setFormOpen(true);
  }

  function closeFormDialog() {
    if (!saving) {
      setFormOpen(false);
      setEditingTemplate(null);
    }
  }

  async function handleSaveTemplate(event: React.FormEvent) {
    event.preventDefault();
    const amountMinorUnits = parseAmountInputToMinorUnits(amount);
    const day = Number(dayOfMonth);
    if (
      !name.trim() ||
      !categoryId ||
      amountMinorUnits === null ||
      amountMinorUnits <= 0 ||
      !Number.isInteger(day) ||
      day < 1 ||
      day > 31 ||
      !startDate ||
      (endDate !== "" && endDate < startDate)
    ) {
      setFormError(
        !Number.isInteger(day) || day < 1 || day > 31
          ? "Ayın günü 1 ile 31 arasında bir tam sayı olmalı."
          : "Gider adı, kategori, tutar ve geçerli tarih bilgilerini girin.",
      );
      return;
    }

    setSaving(true);
    setFormError(null);
    const input = {
      categoryId,
      amountMinorUnits,
      vendor: editingTemplate?.vendor ?? null,
      description: name.trim(),
      dayOfMonth: day,
      startDate,
      endDate: endDate || null,
    };

    try {
      if (editingTemplate) {
        await updateRecurringExpenseTemplate(editingTemplate.id, input);
        showToast("Şablon güncellendi.", "success");
      } else {
        await createRecurringExpenseTemplate({ ...input, branchId: null });
        showToast("Şablon oluşturuldu.", "success");
      }
      setFormOpen(false);
      setEditingTemplate(null);
      load();
    } catch {
      setFormError(editingTemplate ? "Şablon güncellenemedi." : "Tekrarlayan gider şablonu oluşturulamadı.");
    } finally {
      setSaving(false);
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
      showToast("Şablon pasife alındı.", "success");
    } catch {
      showToast("Şablon pasife alınamadı.", "error");
    } finally {
      setDeactivating(false);
      setDeactivateTarget(null);
    }
  }

  async function handleActivate(template: RecurringExpenseTemplate) {
    setActivatingTemplateId(template.id);
    try {
      await activateRecurringExpenseTemplate(template.id);
      setTemplates((current) => current.map((item) => (item.id === template.id ? { ...item, active: true } : item)));
      showToast("Şablon aktifleştirildi.", "success");
    } catch {
      showToast("Şablon aktifleştirilemedi.", "error");
    } finally {
      setActivatingTemplateId(null);
    }
  }

  async function handleConfirmDelete() {
    if (!deleteTarget) {
      return;
    }
    setDeleting(true);
    try {
      await deleteRecurringExpenseTemplate(deleteTarget.id);
      setTemplates((current) => current.filter((template) => template.id !== deleteTarget.id));
      showToast("Şablon silindi. Geçmiş gider kayıtları korundu.", "success");
    } catch {
      showToast("Şablon silinemedi.", "error");
    } finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
  }

  return (
    <section className={`${styles.section} ${styles.panel}`}>
      <PageHeader
        title="Tekrarlayan Giderler"
        description="Dönemi geldiğinde otomatik gider oluşturan şablonları yönetin."
        actions={
          <Button onClick={openCreateDialog}>
            <Plus size={16} aria-hidden="true" /> Şablon Ekle
          </Button>
        }
      />

      {loading ? (
        <TableSkeleton />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : templates.length === 0 ? (
        <EmptyState title="Tekrarlayan gider şablonu yok" />
      ) : (
        <div className={expenseStyles.recurringTable}>
          <Table>
            <thead>
              <tr>
                <th>Gider</th>
                <th>Kategori</th>
                <th>Tutar</th>
                <th>Tekrar</th>
                <th>Sonraki Tarih / Ayın Günü</th>
                <th>Durum</th>
                <th className={expenseStyles.actionsHeader}>İşlemler</th>
              </tr>
            </thead>
            <tbody>
              {templates.map((template) => {
                const nextDate = nextDueDate(template, branchIsoDate(branchTimeZone));
                return (
                  <tr key={template.id}>
                    <td className={`${tableStyles.primary} ${expenseStyles.templateName}`} title={templateName(template)}>
                      {templateName(template)}
                    </td>
                    <td className={tableStyles.muted}>{template.categoryName ?? "—"}</td>
                    <td className={expenseStyles.templateAmount}>{formatPriceMinorUnits(template.amountMinorUnits)}</td>
                    <td className={expenseStyles.recurrenceInfo}>
                      Aylık <span aria-hidden="true">·</span> Her ayın {template.dayOfMonth}. günü
                    </td>
                    <td className={tableStyles.muted}>{nextDate ? formatIsoDate(nextDate) : `Her ayın ${template.dayOfMonth}. günü`}</td>
                    <td>
                      <Badge tone={template.active ? "success" : "neutral"}>{template.active ? "Aktif" : "Pasif"}</Badge>
                    </td>
                    <td className={expenseStyles.actionsCell}>
                      <div className={tableStyles.actions}>
                        <Button size="md" variant="ghost" onClick={() => openEditDialog(template)}>
                          <Pencil size={15} aria-hidden="true" /> Düzenle
                        </Button>
                        {template.active ? (
                          <Button size="md" variant="ghost" onClick={() => setDeactivateTarget(template)}>
                            Pasife Al
                          </Button>
                        ) : (
                          <Button
                            size="md"
                            variant="secondary"
                            disabled={activatingTemplateId === template.id}
                            onClick={() => void handleActivate(template)}
                          >
                            {activatingTemplateId === template.id ? "Aktifleştiriliyor…" : "Aktifleştir"}
                          </Button>
                        )}
                        <Button
                          className={expenseStyles.dangerAction}
                          size="md"
                          variant="ghost"
                          onClick={() => setDeleteTarget(template)}
                        >
                          <Trash2 size={15} aria-hidden="true" /> Sil
                        </Button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </Table>
        </div>
      )}

      {formOpen ? (
        <Dialog onClose={closeFormDialog} labelledBy={dialogTitleId} size="lg">
          <h2 id={dialogTitleId} className={styles.sectionTitle}>
            {editingTemplate ? "Şablonu Düzenle" : "Yeni Şablon"}
          </h2>
          <form className={`${styles.section} ${expenseStyles.templateForm}`} onSubmit={handleSaveTemplate}>
            <div className={expenseStyles.templateFormGrid}>
              <FormField label="Gider adı" required>
                {(controlProps) => (
                  <Input {...controlProps} value={name} onChange={(event) => setName(event.target.value)} placeholder="Örn. Aylık kira" required />
                )}
              </FormField>
              <FormField label="Kategori" required>
                {(controlProps) => (
                  <Select {...controlProps} value={categoryId} onChange={(event) => setCategoryId(event.target.value)} required>
                    <option value="">Seçin</option>
                    {categories
                      .filter((category) => category.active || category.id === categoryId)
                      .map((category) => (
                        <option key={category.id} value={category.id}>
                          {category.name}
                        </option>
                      ))}
                  </Select>
                )}
              </FormField>
              <FormField label="Tutar (₺)" required>
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    inputMode="decimal"
                    value={amount}
                    onChange={(event) => setAmount(event.target.value)}
                    onBlur={() => setAmount((current) => formatAmountInput(current))}
                    placeholder="0,00"
                    required
                  />
                )}
              </FormField>
              <FormField
                label="Ayın günü"
                hint="1–31 arasında girin. Kısa aylarda ayın son günü kullanılır."
                error={
                  dayOfMonth !== "" &&
                  (!Number.isInteger(Number(dayOfMonth)) || Number(dayOfMonth) < 1 || Number(dayOfMonth) > 31)
                    ? "1 ile 31 arasında bir tam sayı girin."
                    : undefined
                }
                required
              >
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    type="number"
                    inputMode="numeric"
                    min={1}
                    max={31}
                    step={1}
                    value={dayOfMonth}
                    onChange={(event) => setDayOfMonth(event.target.value)}
                    required
                  />
                )}
              </FormField>
              <FormField label="Başlangıç tarihi" required>
                {(controlProps) => (
                  <Input {...controlProps} type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} required />
                )}
              </FormField>
              <FormField label="Bitiş tarihi" hint="Süresiz devam etmesi için boş bırakın.">
                {(controlProps) => (
                  <Input {...controlProps} type="date" min={startDate} value={endDate} onChange={(event) => setEndDate(event.target.value)} />
                )}
              </FormField>
            </div>

            {formError ? <ErrorState message={formError} /> : null}

            <div className={expenseStyles.formActions}>
              <Button variant="secondary" onClick={closeFormDialog} disabled={saving}>
                Vazgeç
              </Button>
              <Button type="submit" disabled={saving}>
                {saving ? "Kaydediliyor…" : editingTemplate ? "Değişiklikleri Kaydet" : "Şablon Ekle"}
              </Button>
            </div>
          </form>
        </Dialog>
      ) : null}

      {deactivateTarget ? (
        <ConfirmDialog
          title="Şablonu Pasife Al"
          message="Şablon artık yeni gider üretmeyecek. Daha önce oluşmuş gider kayıtları değişmeden korunacak."
          confirmLabel="Pasife Al"
          confirmLoading={deactivating}
          onConfirm={handleConfirmDeactivate}
          onCancel={() => setDeactivateTarget(null)}
        />
      ) : null}

      {deleteTarget ? (
        <ConfirmDialog
          title="Şablonu Sil"
          message={`“${templateName(deleteTarget)}” şablonu silinecek ve gelecekte gider üretmeyecek. Geçmişte oluşmuş gider kayıtları korunur. Bu işlem geri alınamaz.`}
          confirmLabel="Şablonu Sil"
          tone="danger"
          confirmLoading={deleting}
          onConfirm={handleConfirmDelete}
          onCancel={() => setDeleteTarget(null)}
        />
      ) : null}
    </section>
  );
}
