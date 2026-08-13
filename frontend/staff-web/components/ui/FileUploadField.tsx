"use client";

import { useState, type ChangeEvent } from "react";
import FormField from "./FormField";
import Button from "./Button";
import styles from "./FileUploadField.module.css";

type Props = {
  label: string;
  value: string | null;
  onChange: (url: string | null) => void;
  upload: (file: File) => Promise<string>;
  accept: string;
  hint?: string;
  /** Receipts (may be a PDF) show a "view" link instead of a thumbnail. */
  previewAsImage?: boolean;
};

/**
 * Gap-analysis #15 (Section 3.2/16.1): file picked -> uploaded immediately -> `value`
 * becomes the URL MediaStoragePort returned. No manual URL text entry anywhere - the
 * caller's create/update form keeps sending a plain string, unaware it came from an
 * upload rather than being typed.
 *
 * Non-image files (receipts) are not linked out to directly: unlike product images,
 * receipt files are not on a public URL (see backend MediaResourceConfig), so `value`
 * alone is not fetchable without the caller's staff session - only a confirmation is
 * shown here, not a clickable preview.
 */
export default function FileUploadField({ label, value, onChange, upload, accept, hint, previewAsImage = true }: Props) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) {
      return;
    }
    setUploading(true);
    setError(null);
    try {
      const url = await upload(file);
      onChange(url);
    } catch {
      setError("Dosya yüklenemedi. Dosya türünü ve boyutunu kontrol edin.");
    } finally {
      setUploading(false);
    }
  }

  return (
    <FormField label={label} hint={hint} error={error ?? undefined}>
      {(controlProps) => (
        <div className={styles.wrapper}>
          {value ? (
            <div className={styles.preview}>
              {previewAsImage ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={value} alt="" className={styles.thumb} />
              ) : (
                <span className={styles.fileLink}>Dosya yüklendi</span>
              )}
              <Button type="button" variant="ghost" size="md" onClick={() => onChange(null)} disabled={uploading}>
                Kaldır
              </Button>
            </div>
          ) : null}
          <input
            {...controlProps}
            type="file"
            accept={accept}
            onChange={handleFileChange}
            disabled={uploading}
            className={styles.fileInput}
          />
          {uploading ? <p className={styles.status}>Yükleniyor…</p> : null}
        </div>
      )}
    </FormField>
  );
}
