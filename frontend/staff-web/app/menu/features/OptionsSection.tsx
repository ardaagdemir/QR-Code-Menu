"use client";

import { useEffect, useId, useState } from "react";
import { ArrowDown, ArrowUp, Pencil, Plus, Trash2 } from "lucide-react";
import {
  createOption,
  deleteOption,
  listOptions,
  reorderOptions,
  updateOption,
  type OptionAdmin,
} from "@/lib/api";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import EmptyState from "@/components/ui/EmptyState";
import { useToast } from "@/components/ui/ToastProvider";
import styles from "@/styles/admin.module.css";
import menuStyles from "../menu.module.css";

type Props = {
  productId: string;
  optionGroupId: string;
};

type FormValues = {
  name: string;
  priceDelta: string;
};

function emptyForm(): FormValues {
  return { name: "", priceDelta: "0" };
}

function formFromOption(option: OptionAdmin): FormValues {
  return { name: option.name, priceDelta: (option.priceDeltaMinorUnits / 100).toFixed(2).replace(".", ",") };
}

function parsePriceDeltaMinorUnits(value: string): number | null {
  const normalized = value.trim() === "" ? "0" : value.replace(",", ".");
  const asNumber = Number(normalized);
  if (!Number.isFinite(asNumber)) {
    return null;
  }
  return Math.round(asNumber * 100);
}

/** Menü Yönetimi'nin option paneli: bir option group'un altındaki option'ların create/rename/reorder/delete'i. */
export default function OptionsSection({ productId, optionGroupId }: Props) {
  const { showToast } = useToast();
  const dialogTitleId = useId();
  const editDialogTitleId = useId();

  const [options, setOptions] = useState<OptionAdmin[]>([]);
  const [loading, setLoading] = useState(true);

  const [createOpen, setCreateOpen] = useState(false);
  const [formValues, setFormValues] = useState<FormValues>(emptyForm());
  const [saving, setSaving] = useState(false);

  const [editTarget, setEditTarget] = useState<OptionAdmin | null>(null);
  const [editValues, setEditValues] = useState<FormValues>(emptyForm());

  const [reorderingId, setReorderingId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<OptionAdmin | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    listOptions(optionGroupId)
      .then(setOptions)
      .catch(() => showToast("Seçenekler yüklenemedi.", "error"))
      .finally(() => setLoading(false));
  }, [optionGroupId, showToast]);

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    if (!formValues.name.trim()) {
      return;
    }
    const priceDeltaMinorUnits = parsePriceDeltaMinorUnits(formValues.priceDelta);
    if (priceDeltaMinorUnits === null) {
      showToast("Geçerli bir fiyat farkı girin.", "error");
      return;
    }
    setSaving(true);
    try {
      const option = await createOption(productId, optionGroupId, { name: formValues.name.trim(), priceDeltaMinorUnits });
      setOptions((current) => [...current, option]);
      setFormValues(emptyForm());
      setCreateOpen(false);
      showToast("Seçenek eklendi.", "success");
    } catch {
      showToast("Seçenek eklenemedi.", "error");
    } finally {
      setSaving(false);
    }
  }

  async function handleConfirmEdit(event: React.FormEvent) {
    event.preventDefault();
    if (!editTarget || !editValues.name.trim()) {
      return;
    }
    const priceDeltaMinorUnits = parsePriceDeltaMinorUnits(editValues.priceDelta);
    if (priceDeltaMinorUnits === null) {
      showToast("Geçerli bir fiyat farkı girin.", "error");
      return;
    }
    setSaving(true);
    try {
      const updated = await updateOption(editTarget.id, { name: editValues.name.trim(), priceDeltaMinorUnits });
      setOptions((current) => current.map((o) => (o.id === updated.id ? updated : o)));
      setEditTarget(null);
      showToast("Seçenek güncellendi.", "success");
    } catch {
      showToast("Seçenek güncellenemedi.", "error");
    } finally {
      setSaving(false);
    }
  }

  async function handleMove(index: number, direction: -1 | 1) {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= options.length) {
      return;
    }
    const reordered = [...options];
    [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
    setReorderingId(options[index].id);
    try {
      const result = await reorderOptions(optionGroupId, reordered.map((o) => o.id));
      setOptions(result);
    } catch {
      showToast("Seçenek sırası güncellenemedi.", "error");
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
      await deleteOption(deleteTarget.id);
      setOptions((current) => current.filter((o) => o.id !== deleteTarget.id));
      showToast("Seçenek silindi.", "success");
    } catch {
      showToast("Seçenek silinemedi.", "error");
    } finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
  }

  if (loading) {
    return <p className={menuStyles.optionsLoading}>Seçenekler yükleniyor…</p>;
  }

  return (
    <div className={menuStyles.optionsPanel}>
      {options.length === 0 ? (
        <EmptyState title="Bu grupta seçenek yok" />
      ) : (
        <ul className={menuStyles.optionList}>
          {options.map((option, index) => (
            <li key={option.id} className={menuStyles.optionRow}>
              <span className={menuStyles.optionName}>{option.name}</span>
              <span className={menuStyles.optionPrice}>
                {option.priceDeltaMinorUnits === 0 ? "Ücretsiz" : `+${(option.priceDeltaMinorUnits / 100).toFixed(2)} ₺`}
              </span>
              <div className={menuStyles.optionRowActions}>
                <button
                  type="button"
                  className={menuStyles.rowIconButton}
                  disabled={index === 0 || reorderingId !== null}
                  onClick={() => handleMove(index, -1)}
                  aria-label={`${option.name} seçeneğini yukarı taşı`}
                  title="Yukarı taşı"
                >
                  <ArrowUp size={13} aria-hidden="true" />
                </button>
                <button
                  type="button"
                  className={menuStyles.rowIconButton}
                  disabled={index === options.length - 1 || reorderingId !== null}
                  onClick={() => handleMove(index, 1)}
                  aria-label={`${option.name} seçeneğini aşağı taşı`}
                  title="Aşağı taşı"
                >
                  <ArrowDown size={13} aria-hidden="true" />
                </button>
                <button
                  type="button"
                  className={menuStyles.rowIconButton}
                  onClick={() => {
                    setEditTarget(option);
                    setEditValues(formFromOption(option));
                  }}
                  aria-label={`${option.name} seçeneğini düzenle`}
                  title="Düzenle"
                >
                  <Pencil size={13} aria-hidden="true" />
                </button>
                <button
                  type="button"
                  className={`${menuStyles.rowIconButton} ${menuStyles.rowIconButtonDanger}`}
                  onClick={() => setDeleteTarget(option)}
                  aria-label={`${option.name} seçeneğini sil`}
                  title="Sil"
                >
                  <Trash2 size={13} aria-hidden="true" />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Button size="md" variant="secondary" onClick={() => { setFormValues(emptyForm()); setCreateOpen(true); }}>
        <Plus size={14} aria-hidden="true" /> Seçenek Ekle
      </Button>

      {createOpen ? (
        <Dialog onClose={() => setCreateOpen(false)} labelledBy={dialogTitleId} size="sm">
          <h2 id={dialogTitleId} className={styles.sectionTitle}>
            Yeni Seçenek
          </h2>
          <form className={styles.section} onSubmit={handleCreate}>
            <FormField label="Seçenek adı" required>
              {(controlProps) => (
                <Input {...controlProps} value={formValues.name} onChange={(e) => setFormValues({ ...formValues, name: e.target.value })} required />
              )}
            </FormField>
            <FormField label="Fiyat farkı (₺)" hint="Ücretsizse 0 bırakın">
              {(controlProps) => (
                <Input
                  {...controlProps}
                  inputMode="decimal"
                  value={formValues.priceDelta}
                  onChange={(e) => setFormValues({ ...formValues, priceDelta: e.target.value })}
                />
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
            Seçeneği Düzenle
          </h2>
          <form className={styles.section} onSubmit={handleConfirmEdit}>
            <FormField label="Seçenek adı" required>
              {(controlProps) => (
                <Input {...controlProps} value={editValues.name} onChange={(e) => setEditValues({ ...editValues, name: e.target.value })} required />
              )}
            </FormField>
            <FormField label="Fiyat farkı (₺)" hint="Ücretsizse 0 bırakın">
              {(controlProps) => (
                <Input
                  {...controlProps}
                  inputMode="decimal"
                  value={editValues.priceDelta}
                  onChange={(e) => setEditValues({ ...editValues, priceDelta: e.target.value })}
                />
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
          title="Seçeneği Sil"
          message={`"${deleteTarget.name}" seçeneği kalıcı olarak silinecek.`}
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
