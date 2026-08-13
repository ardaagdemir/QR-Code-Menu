"use client";

import { useId, useState } from "react";
import { createExpense, uploadReceiptImage, type Branch, type Expense, type ExpenseCategory } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import ErrorState from "@/components/ui/ErrorState";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import FileUploadField from "@/components/ui/FileUploadField";
import { useToast } from "@/components/ui/ToastProvider";
import styles from "@/styles/admin.module.css";

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

type Props = {
  categories: ExpenseCategory[];
  accessibleBranches: Branch[];
  isBusinessAdmin: boolean;
  onCreated: (expense: Expense) => void;
};

/** Gider Yönetimi'nin yeni gider ekleme paneli (product-requirements.md Section 16). */
export default function ExpenseForm({ categories, accessibleBranches, isBusinessAdmin, onCreated }: Props) {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [open, setOpen] = useState(false);
  const [branchId, setBranchId] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(todayIsoDate());
  const [vendor, setVendor] = useState("");
  const [receiptImageUrl, setReceiptImageUrl] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const activeCategories = categories.filter((category) => category.active);

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    const amountMinorUnits = Math.round(Number(amount.replace(",", ".")) * 100);
    if (!categoryId || !Number.isFinite(amountMinorUnits) || amountMinorUnits <= 0) {
      setFormError("Kategori seçin ve geçerli bir tutar girin.");
      return;
    }
    setCreating(true);
    setFormError(null);
    try {
      const expense = await createExpense({
        branchId: branchId || null,
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
    <section className={styles.section}>
      <PageHeader title="Yeni Gider" actions={<Button onClick={() => setOpen(true)}>+ Gider Ekle</Button>} />

      {open ? (
        <Dialog onClose={() => setOpen(false)} labelledBy={dialogTitleId}>
          <h2 id={dialogTitleId} className={styles.sectionTitle}>
            Yeni Gider
          </h2>
          <form className={styles.section} onSubmit={handleCreate}>
            <FormField label="Şube">
              {(controlProps) => (
                <Select {...controlProps} value={branchId} onChange={(event) => setBranchId(event.target.value)}>
                  {isBusinessAdmin ? <option value="">İşletme geneli</option> : null}
                  {accessibleBranches.map((branch) => (
                    <option key={branch.id} value={branch.id}>
                      {branch.name}
                    </option>
                  ))}
                </Select>
              )}
            </FormField>
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
            <FormField label="Tarih">
              {(controlProps) => <Input {...controlProps} type="date" value={date} onChange={(event) => setDate(event.target.value)} />}
            </FormField>
            <FormField label="Satıcı (opsiyonel)">
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

            <Button type="submit" disabled={creating}>
              {creating ? "Oluşturuluyor…" : "Gider Ekle"}
            </Button>
          </form>
        </Dialog>
      ) : null}
    </section>
  );
}
