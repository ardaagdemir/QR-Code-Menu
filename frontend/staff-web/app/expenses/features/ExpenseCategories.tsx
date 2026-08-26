"use client";

import { useId, useState } from "react";
import { Pencil, Plus, RotateCcw, Tags, X } from "lucide-react";
import {
  ApiError,
  activateExpenseCategory,
  createExpenseCategory,
  deactivateExpenseCategory,
  updateExpenseCategory,
  type ExpenseCategory,
} from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import EmptyState from "@/components/ui/EmptyState";
import { useToast } from "@/components/ui/ToastProvider";
import styles from "@/styles/admin.module.css";
import expenseStyles from "../expenses.module.css";

type Props = {
  categories: ExpenseCategory[];
  isBusinessAdmin: boolean;
  onCategoryCreated: (category: ExpenseCategory) => void;
  onCategoryRenamed: (category: ExpenseCategory) => void;
  onCategoryDeactivated: (categoryId: string) => void;
  onCategoryActivated: (categoryId: string) => void;
};

/** Gider Yönetimi'nin kategori paneli (product-requirements.md Section 16). */
export default function ExpenseCategories({
  categories,
  isBusinessAdmin,
  onCategoryCreated,
  onCategoryRenamed,
  onCategoryDeactivated,
  onCategoryActivated,
}: Props) {
  const { showToast } = useToast();
  const dialogTitleId = useId();
  const renameDialogTitleId = useId();

  const [createOpen, setCreateOpen] = useState(false);
  const [categoryName, setCategoryName] = useState("");
  const [creating, setCreating] = useState(false);
  const [renameTarget, setRenameTarget] = useState<ExpenseCategory | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [renaming, setRenaming] = useState(false);
  const [deactivateTarget, setDeactivateTarget] = useState<ExpenseCategory | null>(null);
  const [deactivating, setDeactivating] = useState(false);
  const [activatingId, setActivatingId] = useState<string | null>(null);
  const sortedCategories = [...categories].sort((a, b) => a.name.localeCompare(b.name, "tr"));

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
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        showToast("Bu isimde bir kategori zaten var.", "error");
      } else {
        showToast("Kategori oluşturulamadı.", "error");
      }
    } finally {
      setCreating(false);
    }
  }

  function openRename(category: ExpenseCategory) {
    setRenameTarget(category);
    setRenameValue(category.name);
  }

  async function handleConfirmRename(event: React.FormEvent) {
    event.preventDefault();
    if (!renameTarget || !renameValue.trim()) {
      return;
    }
    setRenaming(true);
    try {
      const updated = await updateExpenseCategory(renameTarget.id, renameValue.trim());
      onCategoryRenamed(updated);
      setRenameTarget(null);
      showToast("Kategori yeniden adlandırıldı.", "success");
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        showToast("Bu isimde bir kategori zaten var.", "error");
      } else {
        showToast("Kategori yeniden adlandırılamadı.", "error");
      }
    } finally {
      setRenaming(false);
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

  async function handleActivate(category: ExpenseCategory) {
    setActivatingId(category.id);
    try {
      await activateExpenseCategory(category.id);
      onCategoryActivated(category.id);
      showToast("Kategori aktifleştirildi.", "success");
    } catch {
      showToast("Kategori aktifleştirilemedi.", "error");
    } finally {
      setActivatingId(null);
    }
  }

  return (
    <section className={`${styles.section} ${styles.panel}`}>
      <PageHeader
        title="Gider Kategorileri"
        description="Kayıtlarda kullanılabilecek kategoriler; pasif kategoriler yeni kayıtlarda seçilemez."
        actions={
          <Button variant="secondary" onClick={() => setCreateOpen(true)}>
            <Plus size={16} aria-hidden="true" /> Kategori Ekle
          </Button>
        }
      />

      {sortedCategories.length === 0 ? (
        <EmptyState icon={<Tags size={20} />} title="Henüz gider kategorisi yok" description="İlk gider kategorisini ekleyin." />
      ) : (
        <div className={`${styles.rowActions} ${expenseStyles.categoryList}`}>
          {sortedCategories.map((category) => (
            <Badge key={category.id} tone={category.active ? "neutral" : "warning"}>
              <span className={category.active ? undefined : expenseStyles.categoryInactive}>
                {category.active ? category.name : `${category.name} (Pasif)`}
              </span>
              {isBusinessAdmin ? (
                <button
                  type="button"
                  onClick={(event) => {
                    event.stopPropagation();
                    openRename(category);
                  }}
                  className={`${styles.tagRemove} ${expenseStyles.categoryAction}`}
                  aria-label={`${category.name} kategorisini yeniden adlandır`}
                  title="Yeniden adlandır"
                >
                  <Pencil size={13} aria-hidden="true" />
                </button>
              ) : null}
              {isBusinessAdmin && category.active ? (
                <button
                  type="button"
                  onClick={(event) => {
                    event.stopPropagation();
                    setDeactivateTarget(category);
                  }}
                  className={`${styles.tagRemove} ${expenseStyles.categoryRemove}`}
                  aria-label={`${category.name} kategorisini devre dışı bırak`}
                  title="Pasife al"
                >
                  <X size={13} aria-hidden="true" />
                </button>
              ) : null}
              {isBusinessAdmin && !category.active ? (
                <button
                  type="button"
                  onClick={(event) => {
                    event.stopPropagation();
                    handleActivate(category);
                  }}
                  disabled={activatingId === category.id}
                  className={`${styles.tagRemove} ${expenseStyles.categoryAction} ${expenseStyles.categoryReactivate}`}
                  aria-label={`${category.name} kategorisini aktifleştir`}
                  title="Aktifleştir"
                >
                  <RotateCcw size={13} aria-hidden="true" />
                </button>
              ) : null}
            </Badge>
          ))}
        </div>
      )}

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

      {renameTarget ? (
        <Dialog onClose={() => setRenameTarget(null)} labelledBy={renameDialogTitleId}>
          <h2 id={renameDialogTitleId} className={styles.sectionTitle}>
            Kategoriyi Yeniden Adlandır
          </h2>
          <form className={styles.section} onSubmit={handleConfirmRename}>
            <FormField label="Kategori adı" required>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  value={renameValue}
                  onChange={(event) => setRenameValue(event.target.value)}
                  required
                />
              )}
            </FormField>
            <Button type="submit" disabled={renaming}>
              {renaming ? "Kaydediliyor…" : "Kaydet"}
            </Button>
          </form>
        </Dialog>
      ) : null}

      {deactivateTarget ? (
        <ConfirmDialog
          title="Kategoriyi Devre Dışı Bırak"
          message={`"${deactivateTarget.name}" kategorisi devre dışı bırakılacak ve yeni giderlerde/tekrarlayan şablonlarda seçilemeyecek. Kategoriyi daha sonra tekrar aktifleştirebilirsiniz.`}
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
