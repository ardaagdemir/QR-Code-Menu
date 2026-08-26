"use client";

import { useEffect, useId, useState } from "react";
import { ArrowDown, ArrowUp, ChevronDown, ChevronRight, Pencil, Plus, Trash2 } from "lucide-react";
import {
  createOptionGroup,
  deleteOptionGroup,
  listOptionGroups,
  reorderOptionGroups,
  updateOptionGroup,
  type OptionGroupAdmin,
} from "@/lib/api";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import EmptyState from "@/components/ui/EmptyState";
import { useToast } from "@/components/ui/ToastProvider";
import OptionsSection from "./OptionsSection";
import styles from "@/styles/admin.module.css";
import menuStyles from "../menu.module.css";

type Props = {
  productId: string;
};

type FormValues = {
  name: string;
  selectionType: "SINGLE" | "MULTIPLE";
};

function emptyForm(): FormValues {
  return { name: "", selectionType: "SINGLE" };
}

const SELECTION_TYPE_LABELS: Record<FormValues["selectionType"], string> = {
  SINGLE: "Tekli seçim",
  MULTIPLE: "Çoklu seçim",
};

/** Menü Yönetimi'nin option group paneli: bir ürünün option group'larının create/rename/reorder/delete'i,
 * her grubun altında OptionsSection ile genişleyen option listesi. */
export default function OptionGroupsSection({ productId }: Props) {
  const { showToast } = useToast();
  const dialogTitleId = useId();
  const editDialogTitleId = useId();

  const [groups, setGroups] = useState<OptionGroupAdmin[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedGroupId, setExpandedGroupId] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [formValues, setFormValues] = useState<FormValues>(emptyForm());
  const [saving, setSaving] = useState(false);

  const [editTarget, setEditTarget] = useState<OptionGroupAdmin | null>(null);
  const [editValues, setEditValues] = useState<FormValues>(emptyForm());

  const [reorderingId, setReorderingId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<OptionGroupAdmin | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    listOptionGroups(productId)
      .then(setGroups)
      .catch(() => showToast("Option grupları yüklenemedi.", "error"))
      .finally(() => setLoading(false));
  }, [productId, showToast]);

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    if (!formValues.name.trim()) {
      return;
    }
    setSaving(true);
    try {
      const group = await createOptionGroup(productId, { name: formValues.name.trim(), selectionType: formValues.selectionType });
      setGroups((current) => [...current, group]);
      setFormValues(emptyForm());
      setCreateOpen(false);
      showToast("Option grubu eklendi.", "success");
    } catch {
      showToast("Option grubu eklenemedi.", "error");
    } finally {
      setSaving(false);
    }
  }

  async function handleConfirmEdit(event: React.FormEvent) {
    event.preventDefault();
    if (!editTarget || !editValues.name.trim()) {
      return;
    }
    setSaving(true);
    try {
      const updated = await updateOptionGroup(editTarget.id, {
        name: editValues.name.trim(),
        selectionType: editValues.selectionType,
      });
      setGroups((current) => current.map((g) => (g.id === updated.id ? updated : g)));
      setEditTarget(null);
      showToast("Option grubu güncellendi.", "success");
    } catch {
      showToast("Option grubu güncellenemedi.", "error");
    } finally {
      setSaving(false);
    }
  }

  async function handleMove(index: number, direction: -1 | 1) {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= groups.length) {
      return;
    }
    const reordered = [...groups];
    [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
    setReorderingId(groups[index].id);
    try {
      const result = await reorderOptionGroups(productId, reordered.map((g) => g.id));
      setGroups(result);
    } catch {
      showToast("Option grubu sırası güncellenemedi.", "error");
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
      await deleteOptionGroup(deleteTarget.id);
      setGroups((current) => current.filter((g) => g.id !== deleteTarget.id));
      if (expandedGroupId === deleteTarget.id) {
        setExpandedGroupId(null);
      }
      showToast("Option grubu silindi.", "success");
    } catch {
      showToast("Option grubu silinemedi.", "error");
    } finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
  }

  if (loading) {
    return <p className={menuStyles.optionsLoading}>Option grupları yükleniyor…</p>;
  }

  return (
    <div className={menuStyles.optionGroupsPanel}>
      {groups.length === 0 ? (
        <EmptyState title="Bu üründe option grubu yok" description="Boyut, ekstra malzeme gibi seçenekler eklemek için bir grup oluşturun." />
      ) : (
        <ul className={menuStyles.optionGroupList}>
          {groups.map((group, index) => {
            const expanded = expandedGroupId === group.id;
            return (
              <li key={group.id} className={menuStyles.optionGroupItem}>
                <div className={menuStyles.optionGroupHeader}>
                  <button
                    type="button"
                    className={menuStyles.optionGroupToggle}
                    onClick={() => setExpandedGroupId(expanded ? null : group.id)}
                    aria-expanded={expanded}
                  >
                    {expanded ? <ChevronDown size={16} aria-hidden="true" /> : <ChevronRight size={16} aria-hidden="true" />}
                    <span className={menuStyles.optionName}>{group.name}</span>
                    <span className={menuStyles.optionGroupType}>{SELECTION_TYPE_LABELS[group.selectionType]}</span>
                  </button>
                  <div className={menuStyles.optionRowActions}>
                    <button
                      type="button"
                      className={menuStyles.rowIconButton}
                      disabled={index === 0 || reorderingId !== null}
                      onClick={() => handleMove(index, -1)}
                      aria-label={`${group.name} grubunu yukarı taşı`}
                      title="Yukarı taşı"
                    >
                      <ArrowUp size={13} aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      className={menuStyles.rowIconButton}
                      disabled={index === groups.length - 1 || reorderingId !== null}
                      onClick={() => handleMove(index, 1)}
                      aria-label={`${group.name} grubunu aşağı taşı`}
                      title="Aşağı taşı"
                    >
                      <ArrowDown size={13} aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      className={menuStyles.rowIconButton}
                      onClick={() => {
                        setEditTarget(group);
                        setEditValues({ name: group.name, selectionType: group.selectionType });
                      }}
                      aria-label={`${group.name} grubunu düzenle`}
                      title="Düzenle"
                    >
                      <Pencil size={13} aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      className={`${menuStyles.rowIconButton} ${menuStyles.rowIconButtonDanger}`}
                      onClick={() => setDeleteTarget(group)}
                      aria-label={`${group.name} grubunu sil`}
                      title="Sil"
                    >
                      <Trash2 size={13} aria-hidden="true" />
                    </button>
                  </div>
                </div>
                {expanded ? (
                  <div className={menuStyles.optionGroupBody}>
                    <OptionsSection productId={productId} optionGroupId={group.id} />
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}

      <Button size="md" variant="secondary" onClick={() => { setFormValues(emptyForm()); setCreateOpen(true); }}>
        <Plus size={14} aria-hidden="true" /> Option Grubu Ekle
      </Button>

      {createOpen ? (
        <Dialog onClose={() => setCreateOpen(false)} labelledBy={dialogTitleId} size="sm">
          <h2 id={dialogTitleId} className={styles.sectionTitle}>
            Yeni Option Grubu
          </h2>
          <form className={styles.section} onSubmit={handleCreate}>
            <FormField label="Grup adı" required>
              {(controlProps) => (
                <Input {...controlProps} value={formValues.name} onChange={(e) => setFormValues({ ...formValues, name: e.target.value })} required />
              )}
            </FormField>
            <FormField label="Seçim tipi" required>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  value={formValues.selectionType}
                  onChange={(e) => setFormValues({ ...formValues, selectionType: e.target.value as FormValues["selectionType"] })}
                >
                  <option value="SINGLE">Tekli seçim</option>
                  <option value="MULTIPLE">Çoklu seçim</option>
                </Select>
              )}
            </FormField>
            <Button type="submit" disabled={saving}>
              {saving ? "Ekleniyor…" : "Ekle"}
            </Button>
          </form>
        </Dialog>
      ) : null}

      {editTarget ? (
        <Dialog onClose={() => setEditTarget(null)} labelledBy={editDialogTitleId} size="sm">
          <h2 id={editDialogTitleId} className={styles.sectionTitle}>
            Option Grubunu Düzenle
          </h2>
          <form className={styles.section} onSubmit={handleConfirmEdit}>
            <FormField label="Grup adı" required>
              {(controlProps) => (
                <Input {...controlProps} value={editValues.name} onChange={(e) => setEditValues({ ...editValues, name: e.target.value })} required />
              )}
            </FormField>
            <FormField label="Seçim tipi" required>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  value={editValues.selectionType}
                  onChange={(e) => setEditValues({ ...editValues, selectionType: e.target.value as FormValues["selectionType"] })}
                >
                  <option value="SINGLE">Tekli seçim</option>
                  <option value="MULTIPLE">Çoklu seçim</option>
                </Select>
              )}
            </FormField>
            <Button type="submit" disabled={saving}>
              {saving ? "Kaydediliyor…" : "Kaydet"}
            </Button>
          </form>
        </Dialog>
      ) : null}

      {deleteTarget ? (
        <ConfirmDialog
          title="Option Grubunu Sil"
          message={`"${deleteTarget.name}" grubu ve içindeki tüm option'lar kalıcı olarak silinecek.`}
          confirmLabel="Sil"
          tone="danger"
          confirmLoading={deleting}
          onConfirm={handleConfirmDelete}
          onCancel={() => setDeleteTarget(null)}
        />
      ) : null}
    </div>
  );
}
