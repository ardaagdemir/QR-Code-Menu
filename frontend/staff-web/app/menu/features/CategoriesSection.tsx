"use client";

import { useId, useState } from "react";
import { ArrowDown, ArrowUp, Check, Pencil, Plus, Trash2 } from "lucide-react";
import {
  ApiError,
  createMenuCategory,
  deleteMenuCategory,
  renameMenuCategory,
  reorderMenuCategories,
  type MenuCategoryAdmin,
} from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import { useToast } from "@/components/ui/ToastProvider";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";
import menuStyles from "../menu.module.css";

type Props = {
  categories: MenuCategoryAdmin[];
  selectedCategoryId: string | null;
  onSelectCategory: (categoryId: string) => void;
  onCategoryCreated: (category: MenuCategoryAdmin) => void;
  onCategoryRenamed: (category: MenuCategoryAdmin) => void;
  onCategoriesReordered: (categories: MenuCategoryAdmin[]) => void;
  onCategoryDeleted: (categoryId: string) => void;
};

/** Menü Yönetimi'nin kategori paneli - Bölüm 4/Bölüm 5 (Permission.MENU_MANAGE). */
export default function CategoriesSection({
  categories,
  selectedCategoryId,
  onSelectCategory,
  onCategoryCreated,
  onCategoryRenamed,
  onCategoriesReordered,
  onCategoryDeleted,
}: Props) {
  const { showToast } = useToast();
  const dialogTitleId = useId();
  const renameDialogTitleId = useId();

  const [createOpen, setCreateOpen] = useState(false);
  const [categoryName, setCategoryName] = useState("");
  const [creating, setCreating] = useState(false);

  const [renameTarget, setRenameTarget] = useState<MenuCategoryAdmin | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [renaming, setRenaming] = useState(false);

  const [reorderingId, setReorderingId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<MenuCategoryAdmin | null>(null);
  const [deleting, setDeleting] = useState(false);

  async function handleCreateCategory(event: React.FormEvent) {
    event.preventDefault();
    if (!categoryName.trim()) {
      return;
    }
    setCreating(true);
    try {
      const category = await createMenuCategory(categoryName.trim());
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

  function openRename(category: MenuCategoryAdmin) {
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
      const updated = await renameMenuCategory(renameTarget.id, renameValue.trim());
      onCategoryRenamed(updated);
      setRenameTarget(null);
      showToast("Kategori yeniden adlandırıldı.", "success");
    } catch {
      showToast("Kategori yeniden adlandırılamadı.", "error");
    } finally {
      setRenaming(false);
    }
  }

  async function handleMove(index: number, direction: -1 | 1) {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= categories.length) {
      return;
    }
    const reordered = [...categories];
    [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
    const orderedIds = reordered.map((c) => c.id);
    setReorderingId(categories[index].id);
    try {
      const result = await reorderMenuCategories(orderedIds);
      onCategoriesReordered(result);
    } catch {
      showToast("Kategori sırası güncellenemedi.", "error");
    } finally {
      setReorderingId(null);
    }
  }

  async function handleConfirmDelete() {
    if (!deleteTarget) {
      return;
    }
    setDeleting(true);
    try {
      await deleteMenuCategory(deleteTarget.id);
      onCategoryDeleted(deleteTarget.id);
      showToast("Kategori silindi.", "success");
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        showToast("Bu kategoride hâlâ ürün var - önce ürünleri silin veya taşıyın.", "error");
      } else {
        showToast("Kategori silinemedi.", "error");
      }
    } finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
  }

  return (
    <section className={`${styles.section} ${styles.panel} ${menuStyles.categoryPanel}`}>
      <PageHeader
        title="Kategoriler"
        description="Ürün listesini filtrelemek için bir kategori seçin."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus size={16} aria-hidden="true" /> Kategori Ekle
          </Button>
        }
      />

      {categories.length === 0 ? (
        <EmptyState title="Henüz kategori yok" />
      ) : (
        <Table>
          <thead>
            <tr>
              <th>Kategori</th>
            </tr>
          </thead>
          <tbody>
            {categories.map((category, index) => (
              <tr key={category.id}>
                <td>
                  <div className={menuStyles.categoryRow}>
                    <button
                      type="button"
                      className={`${tableStyles.primary} ${styles.linkButton} ${menuStyles.categoryButton} ${
                        selectedCategoryId === category.id ? menuStyles.categoryButtonSelected : ""
                      }`}
                      onClick={() => onSelectCategory(category.id)}
                      aria-pressed={selectedCategoryId === category.id}
                    >
                      {category.name}
                      {selectedCategoryId === category.id ? (
                        <Check className={menuStyles.categoryCheck} size={16} aria-hidden="true" />
                      ) : null}
                    </button>
                    <div className={tableStyles.actions}>
                      <button
                        type="button"
                        className={menuStyles.rowIconButton}
                        disabled={index === 0 || reorderingId !== null}
                        onClick={() => handleMove(index, -1)}
                        aria-label={`${category.name} kategorisini yukarı taşı`}
                        title="Yukarı taşı"
                      >
                        <ArrowUp size={14} aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        className={menuStyles.rowIconButton}
                        disabled={index === categories.length - 1 || reorderingId !== null}
                        onClick={() => handleMove(index, 1)}
                        aria-label={`${category.name} kategorisini aşağı taşı`}
                        title="Aşağı taşı"
                      >
                        <ArrowDown size={14} aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        className={menuStyles.rowIconButton}
                        onClick={() => openRename(category)}
                        aria-label={`${category.name} kategorisini yeniden adlandır`}
                        title="Yeniden adlandır"
                      >
                        <Pencil size={14} aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        className={`${menuStyles.rowIconButton} ${menuStyles.rowIconButtonDanger}`}
                        onClick={() => setDeleteTarget(category)}
                        aria-label={`${category.name} kategorisini sil`}
                        title="Sil"
                      >
                        <Trash2 size={14} aria-hidden="true" />
                      </button>
                    </div>
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
            Yeni Kategori
          </h2>
          <form className={styles.section} onSubmit={handleCreateCategory}>
            <FormField label="Kategori adı" required>
              {(controlProps) => <Input {...controlProps} value={categoryName} onChange={(event) => setCategoryName(event.target.value)} required />}
            </FormField>
            <Button type="submit" disabled={creating}>
              {creating ? "Oluşturuluyor…" : "Kategori Ekle"}
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
              {(controlProps) => <Input {...controlProps} value={renameValue} onChange={(event) => setRenameValue(event.target.value)} required />}
            </FormField>
            <Button type="submit" disabled={renaming}>
              {renaming ? "Kaydediliyor…" : "Kaydet"}
            </Button>
          </form>
        </Dialog>
      ) : null}

      {deleteTarget ? (
        <ConfirmDialog
          title="Kategoriyi Sil"
          message={`"${deleteTarget.name}" kategorisi kalıcı olarak silinecek. Bu işlem geri alınamaz.`}
          confirmLabel="Sil"
          tone="danger"
          confirmLoading={deleting}
          onConfirm={handleConfirmDelete}
          onCancel={() => setDeleteTarget(null)}
        />
      ) : null}
    </section>
  );
}
