"use client";

import { useId, useState } from "react";
import { createMenuCategory, type MenuCategoryAdmin } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import { useToast } from "@/components/ui/ToastProvider";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";

type Props = {
  categories: MenuCategoryAdmin[];
  selectedCategoryId: string | null;
  onSelectCategory: (categoryId: string) => void;
  onCategoryCreated: (category: MenuCategoryAdmin) => void;
};

/** Menü Yönetimi'nin kategori paneli - Bölüm 4/Bölüm 5 (Permission.MENU_MANAGE). */
export default function CategoriesSection({ categories, selectedCategoryId, onSelectCategory, onCategoryCreated }: Props) {
  const { showToast } = useToast();
  const dialogTitleId = useId();

  const [createOpen, setCreateOpen] = useState(false);
  const [categoryName, setCategoryName] = useState("");
  const [creating, setCreating] = useState(false);

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

  return (
    <section className={styles.section}>
      <PageHeader title="Kategoriler" actions={<Button onClick={() => setCreateOpen(true)}>+ Kategori Ekle</Button>} />

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
            {categories.map((category) => (
              <tr key={category.id}>
                <td>
                  <button
                    type="button"
                    className={`${tableStyles.primary} ${styles.linkButton}`}
                    onClick={() => onSelectCategory(category.id)}
                  >
                    {category.name}
                    {selectedCategoryId === category.id ? " (seçili)" : ""}
                  </button>
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
    </section>
  );
}
