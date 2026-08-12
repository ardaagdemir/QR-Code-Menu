"use client";

import { useId } from "react";
import Button from "./Button";
import Dialog from "./Dialog";
import styles from "./ConfirmDialog.module.css";

type Tone = "default" | "danger";

type Props = {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: Tone;
  confirmLoading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

/** Confirm dialog built on Dialog - destructive actions should route through this
 * instead of native confirm() (Bölüm 19.1: "native alert/confirm yerine ürün içi
 * dialog/toast pattern'i", 19.3: "destructive işlemler confirm dialog ister"). */
export default function ConfirmDialog({
  title,
  message,
  confirmLabel = "Onayla",
  cancelLabel = "Vazgeç",
  tone = "default",
  confirmLoading = false,
  onConfirm,
  onCancel,
}: Props) {
  const titleId = useId();

  return (
    <Dialog onClose={onCancel} labelledBy={titleId} size="sm">
      <h2 id={titleId} className={styles.title}>
        {title}
      </h2>
      <p className={styles.message}>{message}</p>
      <div className={styles.actions}>
        <Button variant="secondary" onClick={onCancel} disabled={confirmLoading}>
          {cancelLabel}
        </Button>
        <Button variant={tone === "danger" ? "danger" : "primary"} onClick={onConfirm} disabled={confirmLoading}>
          {confirmLoading ? "..." : confirmLabel}
        </Button>
      </div>
    </Dialog>
  );
}
