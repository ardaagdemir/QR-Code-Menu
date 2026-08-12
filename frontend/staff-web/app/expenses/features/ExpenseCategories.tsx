"use client";

import { useId, useState } from "react";
import { createExpenseCategory, deactivateExpenseCategory, type ExpenseCategory } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import { useToast } from "@/components/ui/ToastProvider";
import styles from "@/styles/admin.module.css";

type Props = {
  categories: ExpenseCategory[];
  isBusinessAdmin: boolean;
  onCategoryCreated: (category: ExpenseCategory) => void;
  onCategoryDeactivated: (categoryId: string) => void;
};

/** Gider Yönetimi'nin kategori paneli (product-requirements.md Section 16). */
export default function ExpenseCategories({ categories, isBusinessAdmin, onCategoryCreated, onCategoryDeactivated }: Props) {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [createOpen, setCreateOpen] = useState(false);
  const [categoryName, setCategoryName] = useState("");
  const [creating, setCreating] = useState(false);
  const [deactivateTarget, setDeactivateTarget] = useState<ExpenseCategory | null>(null);
  const [deactivating, setDeactivating] = useState(false);

  async function handleCreateCategory(event: React.FormEvent) {
    event.preventDefault();
    if (!categoryName.trim()) {
      return;
    }
    setCreating(true);
    try {
      const category = await createExpenseCategory(categoryName.trim());
      setCategoryName("");
      setCreateOpen(false);
      onCategoryCreated(category);
      showToast("Kategori oluşturuldu.", "success");
    } catch {
      showToast("Kategori oluşturulamadı.", "error");
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
      await deactivateExpenseCategory(deactivateTarget.id);
      onCategoryDeactivated(deactivateTarget.id);
      showToast("Kategori devre dışı bırakıldı.", "success");
    } catch {
      showToast("Kategori devre dışı bırakılamadı.", "error");
    } finally {
      setDeactivating(false);
      setDeactivateTarget(null);
    }
  }

  return (
    <section className={styles.section}>
      <PageHeader title="Gider Kategorileri" actions={<Button onClick={() => setCreateOpen(true)}>+ Kategori Ekle</Button>} />

      <div className={styles.rowActions}>
        {categories
          .filter((category) => category.active)
          .map((category) => (
            <Badge key={category.id} tone="neutral">
              {category.name}
              {isBusinessAdmin ? (
                <button
                  type="button"
                  onClick={() => setDeactivateTarget(category)}
                  style={{ marginLeft: 6, border: "none", background: "none", cursor: "pointer", color: "inherit" }}
                >
                  ×
                </button>
              ) : null}
            </Badge>
          ))}
      </div>

      {createOpen ? (
        <Dialog onClose={() => setCreateOpen(false)} labelledBy={dialogTitleId}>
          <h2 id={dialogTitleId} className={styles.sectionTitle}>
            Yeni Gider Kategorisi
          </h2>
          <form className={styles.section} onSubmit={handleCreateCategory}>
            <FormField label="Kategori adı" required>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  value={categoryName}
                  onChange={(event) => setCategoryName(event.target.value)}
                  placeholder="ör. Kira, Elektrik, Malzeme"
                  required
                />
              )}
            </FormField>
            <Button type="submit" disabled={creating}>
              {creating ? "Ekleniyor…" : "Ekle"}
            </Button>
          </form>
        </Dialog>
      ) : null}

      {deactivateTarget ? (
        <ConfirmDialog
          title="Kategoriyi Devre Dışı Bırak"
          message={`"${deactivateTarget.name}" kategorisi devre dışı bırakılacak ve yeni giderlerde seçilemeyecek. Bu işlem geri alınamaz.`}
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
