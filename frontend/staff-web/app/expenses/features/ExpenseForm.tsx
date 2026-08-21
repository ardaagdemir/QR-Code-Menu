"use client";

import { useId, useState } from "react";
import { Plus } from "lucide-react";
import { createExpense, uploadReceiptImage, type Expense, type ExpenseCategory } from "@/lib/api";
import { localIsoDate as todayIsoDate } from "@/lib/time";
import PageHeader from "@/components/ui/PageHeader";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import ErrorState from "@/components/ui/ErrorState";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import FileUploadField from "@/components/ui/FileUploadField";
import { useToast } from "@/components/ui/ToastProvider";
import { formatAmountInput, parseAmountInputToMinorUnits } from "./expenseAmount";
import styles from "@/styles/admin.module.css";
import expenseStyles from "../expenses.module.css";

type Props = {
  categories: ExpenseCategory[];
  onCreated: (expense: Expense) => void;
};

/** Gider Yönetimi'nin yeni gider ekleme paneli (product-requirements.md Section 16). */
export default function ExpenseForm({ categories, onCreated }: Props) {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [open, setOpen] = useState(false);
  const [categoryId, setCategoryId] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(todayIsoDate());
  const [vendor, setVendor] = useState("");
  const [receiptImageUrl, setReceiptImageUrl] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const activeCategories = categories.filter((category) => category.active);

  function closeDialog() {
    if (!creating) {
      setOpen(false);
    }
  }

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    const amountMinorUnits = parseAmountInputToMinorUnits(amount);
    if (!categoryId || amountMinorUnits === null || amountMinorUnits <= 0) {
      setFormError("Kategori seçin ve geçerli bir tutar girin.");
      return;
    }
    setCreating(true);
    setFormError(null);
    try {
      const expense = await createExpense({
        branchId: null,
        categoryId,
        amountMinorUnits,
        incurredAt: date,
        vendor: vendor.trim() || null,
        description: null,
        receiptImageUrl,
      });
      setAmount("");
      setVendor("");
      setReceiptImageUrl(null);
      setOpen(false);
      onCreated(expense);
      showToast("Gider eklendi.", "success");
    } catch {
      setFormError("Gider oluşturulamadı.");
    } finally {
      setCreating(false);
    }
  }

  return (
    <section className={`${styles.section} ${styles.panel} ${expenseStyles.actionPanel}`}>
      <PageHeader
        title="Yeni Gider"
        description="Fiş veya fatura bilgisiyle yeni bir gider kaydı oluşturun."
        actions={
          <Button
            onClick={() => {
              setFormError(null);
              setOpen(true);
            }}
          >
            <Plus size={16} aria-hidden="true" /> Gider Ekle
          </Button>
        }
      />

      {open ? (
        <Dialog onClose={closeDialog} labelledBy={dialogTitleId}>
          <h2 id={dialogTitleId} className={styles.sectionTitle}>
            Yeni Gider
          </h2>
          <form className={`${styles.section} ${expenseStyles.templateForm}`} onSubmit={handleCreate}>
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
            <FormField label="Tarih">
              {(controlProps) => <Input {...controlProps} type="date" value={date} onChange={(event) => setDate(event.target.value)} />}
            </FormField>
            <FormField label="Satıcı / Firma (opsiyonel)">
              {(controlProps) => <Input {...controlProps} value={vendor} onChange={(event) => setVendor(event.target.value)} />}
            </FormField>
            <FileUploadField
              label="Fiş/fatura (opsiyonel)"
              hint="JPEG, PNG, WEBP veya PDF - en fazla 10MB."
              accept="image/jpeg,image/png,image/webp,application/pdf"
              value={receiptImageUrl}
              onChange={setReceiptImageUrl}
              upload={uploadReceiptImage}
              previewAsImage={false}
            />

            {formError ? <ErrorState message={formError} /> : null}

            <div className={expenseStyles.formActions}>
              <Button variant="secondary" onClick={closeDialog} disabled={creating}>
                Vazgeç
              </Button>
              <Button type="submit" disabled={creating}>
                {creating ? "Oluşturuluyor…" : "Gider Ekle"}
              </Button>
            </div>
          </form>
        </Dialog>
      ) : null}
    </section>
  );
}
