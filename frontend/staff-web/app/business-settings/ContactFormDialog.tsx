"use client";

import { useId } from "react";
import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import type { BusinessContactInput } from "@/lib/api";
import styles from "@/styles/admin.module.css";

type Props = {
  mode: "create" | "edit";
  value: BusinessContactInput;
  onChange: (value: BusinessContactInput) => void;
  onSubmit: (event: React.FormEvent) => void;
  onClose: () => void;
  submitting: boolean;
};

/** Create ve Edit aynı form'u paylaşır - tek fark başlık/submit metni ve dışarıdan verilen initial value. */
export default function ContactFormDialog({ mode, value, onChange, onSubmit, onClose, submitting }: Props) {
  const dialogTitleId = useId();
  const title = mode === "create" ? "Yeni Kişi" : "Kişiyi Düzenle";
  const submitLabel = mode === "create"
    ? submitting ? "Ekleniyor…" : "Kişi Ekle"
    : submitting ? "Kaydediliyor…" : "Kaydet";

  return (
    <Dialog onClose={onClose} labelledBy={dialogTitleId}>
      <h2 id={dialogTitleId} className={styles.sectionTitle}>
        {title}
      </h2>
      <form className={styles.section} onSubmit={onSubmit}>
        <FormField label="Ad" required>
          {(controlProps) => (
            <Input
              {...controlProps}
              value={value.name}
              onChange={(event) => onChange({ ...value, name: event.target.value })}
              required
            />
          )}
        </FormField>
        <FormField label="E-posta">
          {(controlProps) => (
            <Input
              {...controlProps}
              type="email"
              value={value.email}
              onChange={(event) => onChange({ ...value, email: event.target.value })}
            />
          )}
        </FormField>

        <div className={styles.rowActions}>
          <label className={styles.checkboxLabel}>
            <input
              type="checkbox"
              checked={value.dailyReportRecipient}
              onChange={(event) => onChange({ ...value, dailyReportRecipient: event.target.checked })}
            />
            Günlük rapor alsın
          </label>
          <label className={styles.checkboxLabel}>
            <input
              type="checkbox"
              checked={value.monthlyReportRecipient}
              onChange={(event) => onChange({ ...value, monthlyReportRecipient: event.target.checked })}
            />
            Aylık rapor alsın
          </label>
        </div>

        <Button type="submit" disabled={submitting}>
          {submitLabel}
        </Button>
      </form>
    </Dialog>
  );
}
