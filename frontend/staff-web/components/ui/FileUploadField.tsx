"use client";

import { useRef, useState, type ChangeEvent } from "react";
import { Upload } from "lucide-react";
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
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [selectedFileName, setSelectedFileName] = useState<string | null>(null);
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
      setSelectedFileName(file.name);
      onChange(url);
    } catch {
      setSelectedFileName(null);
      setError("Dosya yüklenemedi. Dosya türünü ve boyutunu kontrol edin.");
    } finally {
      setUploading(false);
    }
  }

  function handleRemove() {
    setSelectedFileName(null);
    onChange(null);
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
              <Button type="button" variant="ghost" size="md" onClick={handleRemove} disabled={uploading}>
                Kaldır
              </Button>
            </div>
          ) : null}
          <div className={styles.picker}>
            <Button
              type="button"
              variant="secondary"
              size="md"
              disabled={uploading}
              onClick={() => inputRef.current?.click()}
            >
              <Upload size={16} aria-hidden="true" />
              {uploading ? "Yükleniyor…" : value ? "Dosyayı Değiştir" : "Dosya Yükle"}
            </Button>
            <span className={styles.fileName} title={selectedFileName ?? undefined}>
              {selectedFileName ?? (value ? "Dosya hazır" : "Henüz dosya eklenmedi")}
            </span>
          </div>
          <input
            {...controlProps}
            ref={inputRef}
            type="file"
            tabIndex={-1}
            accept={accept}
            onChange={handleFileChange}
            disabled={uploading}
            className={styles.fileInput}
          />
        </div>
      )}
    </FormField>
  );
}
