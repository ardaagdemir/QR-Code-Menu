"use client";

import { useCallback, useEffect, useId, useState } from "react";
import QRCode from "qrcode";
import { Archive, Ban, Download, Pencil, Plus, Printer, QrCode, RefreshCw, RotateCcw, Table2, Trash2 } from "lucide-react";
import {
  ApiError,
  archiveTable,
  createTable,
  deleteTable,
  getActiveQrToken,
  listTables,
  reactivateTable,
  regenerateQrToken,
  renameTable,
  revokeQrToken,
  type QrToken,
  type StaffTable,
} from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import Button from "@/components/ui/Button";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import Dialog from "@/components/ui/Dialog";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { useToast } from "@/components/ui/ToastProvider";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";
import pageStyles from "./page.module.css";

function customerMenuUrl(token: string): string {
  const baseUrl = process.env.NEXT_PUBLIC_CUSTOMER_WEB_URL?.trim() || "http://localhost:3000";
  return `${baseUrl.replace(/\/$/, "")}/t/${encodeURIComponent(token)}`;
}

function safeFilename(value: string): string {
  return value.toLocaleLowerCase("tr-TR").replace(/[^a-z0-9çğıöşü]+/gi, "-").replace(/^-|-$/g, "") || "masa";
}

export default function TablesPage() {
  const { showToast } = useToast();
  const createTableDialogTitleId = useId();
  const renameTableDialogTitleId = useId();

  const [tables, setTables] = useState<StaffTable[]>([]);
  const [qrTokens, setQrTokens] = useState<Record<string, QrToken | null>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyTableId, setBusyTableId] = useState<string | null>(null);
  const [refreshTarget, setRefreshTarget] = useState<{ tableId: string; label: string } | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<{ tableId: string; qrTokenId: string; label: string } | null>(null);
  const [createTableOpen, setCreateTableOpen] = useState(false);
  const [label, setLabel] = useState("");
  const [creating, setCreating] = useState(false);
  const [renameTarget, setRenameTarget] = useState<{ tableId: string } | null>(null);
  const [renameLabel, setRenameLabel] = useState("");
  const [renaming, setRenaming] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<{ tableId: string; label: string } | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<{ tableId: string; label: string } | null>(null);

  const loadTables = useCallback(() => {
    return listTables()
      .then(async (tableList) => {
        const tokens = await Promise.all(tableList.map((table) => getActiveQrToken(table.id)));
        return { tableList, tokens };
      })
      .then(({ tableList, tokens }) => {
        setTables(tableList);
        setQrTokens(Object.fromEntries(tableList.map((table, index) => [table.id, tokens[index]])));
        setError(null);
      })
      .catch(() => setError("Masalar yüklenemedi."))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    void loadTables();
  }, [loadTables]);

  async function handleCreateTable(event: React.FormEvent) {
    event.preventDefault();
    if (!label.trim()) {
      return;
    }
    setCreating(true);
    try {
      await createTable(label.trim());
      setLabel("");
      setCreateTableOpen(false);
      await loadTables();
      showToast("Masa oluşturuldu.", "success");
    } catch {
      showToast("Masa oluşturulamadı.", "error");
    } finally {
      setCreating(false);
    }
  }

  async function handleRenameTable(event: React.FormEvent) {
    event.preventDefault();
    if (!renameTarget || !renameLabel.trim()) {
      return;
    }
    setRenaming(true);
    try {
      const updated = await renameTable(renameTarget.tableId, renameLabel.trim());
      setTables((current) => current.map((table) => (table.id === updated.id ? updated : table)));
      setRenameTarget(null);
      showToast("Masa adı güncellendi.", "success");
    } catch {
      showToast("Masa adı güncellenemedi.", "error");
    } finally {
      setRenaming(false);
    }
  }

  async function handleRegenerate(tableId: string) {
    setBusyTableId(tableId);
    try {
      const token = await regenerateQrToken(tableId);
      setQrTokens((current) => ({ ...current, [tableId]: token }));
      showToast("QR kod oluşturuldu.", "success");
    } catch {
      showToast("QR kod oluşturulamadı.", "error");
    } finally {
      setBusyTableId(null);
    }
  }

  async function handleConfirmRefresh() {
    if (!refreshTarget) {
      return;
    }
    await handleRegenerate(refreshTarget.tableId);
    setRefreshTarget(null);
  }

  async function handleConfirmRevoke() {
    if (!revokeTarget) {
      return;
    }
    setBusyTableId(revokeTarget.tableId);
    try {
      await revokeQrToken(revokeTarget.qrTokenId);
      setQrTokens((current) => ({ ...current, [revokeTarget.tableId]: null }));
      showToast("QR kod iptal edildi.", "success");
    } catch {
      showToast("QR kod iptal edilemedi.", "error");
    } finally {
      setBusyTableId(null);
      setRevokeTarget(null);
    }
  }

  async function handleConfirmDelete() {
    if (!deleteTarget) {
      return;
    }
    setBusyTableId(deleteTarget.tableId);
    try {
      await deleteTable(deleteTarget.tableId);
      setTables((current) => current.filter((table) => table.id !== deleteTarget.tableId));
      setQrTokens((current) => {
        const next = { ...current };
        delete next[deleteTarget.tableId];
        return next;
      });
      showToast("Masa silindi.", "success");
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        showToast("Bu masanın sipariş/ziyaret geçmişi var, silinemez. Bunun yerine arşivleyin.", "error");
      } else {
        showToast("Masa silinemedi.", "error");
      }
    } finally {
      setBusyTableId(null);
      setDeleteTarget(null);
    }
  }

  async function handleConfirmArchive() {
    if (!archiveTarget) {
      return;
    }
    setBusyTableId(archiveTarget.tableId);
    try {
      const updated = await archiveTable(archiveTarget.tableId);
      setTables((current) => current.map((table) => (table.id === updated.id ? updated : table)));
      setQrTokens((current) => ({ ...current, [updated.id]: null }));
      showToast("Masa arşivlendi.", "success");
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        showToast("Bu masada aktif bir ziyaret veya sipariş var, önce onun bitmesini bekleyin.", "error");
      } else {
        showToast("Masa arşivlenemedi.", "error");
      }
    } finally {
      setBusyTableId(null);
      setArchiveTarget(null);
    }
  }

  async function handleReactivate(tableId: string) {
    setBusyTableId(tableId);
    try {
      const updated = await reactivateTable(tableId);
      setTables((current) => current.map((table) => (table.id === updated.id ? updated : table)));
      showToast("Masa tekrar aktifleştirildi.", "success");
    } catch {
      showToast("Masa tekrar aktifleştirilemedi.", "error");
    } finally {
      setBusyTableId(null);
    }
  }

  async function handleDownloadQr(table: StaffTable, token: QrToken) {
    try {
      const dataUrl = await QRCode.toDataURL(customerMenuUrl(token.token), { width: 1024, margin: 3 });
      const link = document.createElement("a");
      link.href = dataUrl;
      link.download = `${safeFilename(table.label)}-qr.png`;
      link.click();
    } catch {
      showToast("QR kod indirilemedi.", "error");
    }
  }

  async function handlePrintQr(table: StaffTable, token: QrToken) {
    const printWindow = window.open("", "_blank", "width=560,height=720");
    if (!printWindow) {
      showToast("Yazdırma penceresi açılamadı.", "error");
      return;
    }
    try {
      const dataUrl = await QRCode.toDataURL(customerMenuUrl(token.token), { width: 900, margin: 3 });
      const heading = document.createElement("h1");
      heading.textContent = table.label;
      const image = document.createElement("img");
      image.src = dataUrl;
      image.alt = `${table.label} QR kodu`;
      const hint = document.createElement("p");
      hint.textContent = "Menüyü açmak için QR kodu okutun";
      const style = printWindow.document.createElement("style");
      style.textContent = "body{font-family:system-ui;text-align:center;padding:32px}h1{font-size:28px}img{width:min(90vw,440px);margin:20px auto}p{font-size:16px}";
      printWindow.document.head.append(style);
      printWindow.document.body.append(heading, image, hint);
      image.onload = () => {
        printWindow.focus();
        printWindow.print();
      };
    } catch {
      printWindow.close();
      showToast("QR kod yazdırılamadı.", "error");
    }
  }

  return (
    <AppShell>
      <main className={`${styles.page} ${pageStyles.page}`}>
        <PageHeader
          title="Masalar"
          description="Aktif şubenin masalarını ve masa bazlı QR kodlarını yönetin."
          actions={
            <Button className={pageStyles.primaryButton} onClick={() => setCreateTableOpen(true)}>
              <Plus size={17} aria-hidden="true" />
              Masa Ekle
            </Button>
          }
        />

        <section className={pageStyles.tablePanel}>
          <div className={pageStyles.panelHeader}>
            <div>
              <h2 className={pageStyles.panelTitle}>Masa Listesi</h2>
              <p className={pageStyles.panelDescription}>Masa QR durumlarını tek ekrandan yönetin.</p>
            </div>
            <span className={pageStyles.tableCount}>{tables.length} masa</span>
          </div>

          <div className={pageStyles.panelBody}>
            {loading ? (
              <TableSkeleton />
            ) : error ? (
              <ErrorState
                message={error}
                onRetry={() => {
                  setLoading(true);
                  void loadTables();
                }}
              />
            ) : tables.length === 0 ? (
              <EmptyState title="Henüz masa yok" description="Aktif şubeye ilk masayı ekleyin." />
            ) : (
              <Table>
                <thead>
                  <tr>
                    <th>Masa</th>
                    <th>QR Durumu</th>
                    <th aria-label="QR işlemleri" />
                  </tr>
                </thead>
                <tbody>
                  {tables.map((table) => {
                    const token = qrTokens[table.id];
                    return (
                      <tr key={table.id}>
                        <td>
                          <div className={pageStyles.tableIdentity}>
                            <span className={pageStyles.tableIcon} aria-hidden="true">
                              <Table2 size={18} />
                            </span>
                            <span className={tableStyles.primary}>{table.label}</span>
                            <Button
                              className={pageStyles.renameButton}
                              size="md"
                              variant="ghost"
                              aria-label={`"${table.label}" masasını yeniden adlandır`}
                              onClick={() => {
                                setRenameTarget({ tableId: table.id });
                                setRenameLabel(table.label);
                              }}
                            >
                              <Pencil size={14} aria-hidden="true" />
                            </Button>
                          </div>
                        </td>
                        <td>
                          {table.active ? (
                            <span className={token ? pageStyles.statusActive : pageStyles.statusEmpty}>
                              <span className={pageStyles.statusDot} aria-hidden="true" />
                              {token ? "Aktif" : "QR yok"}
                            </span>
                          ) : (
                            <span className={pageStyles.statusEmpty}>
                              <span className={pageStyles.statusDot} aria-hidden="true" />
                              Pasif (arşivlendi)
                            </span>
                          )}
                        </td>
                        <td>
                          <div className={tableStyles.actions}>
                            {table.active ? (
                              <>
                                <Button
                                  className={token ? pageStyles.secondaryButton : pageStyles.primaryButton}
                                  size="md"
                                  variant={token ? "secondary" : "primary"}
                                  disabled={busyTableId === table.id}
                                  onClick={() =>
                                    token
                                      ? setRefreshTarget({ tableId: table.id, label: table.label })
                                      : void handleRegenerate(table.id)
                                  }
                                >
                                  {token ? <RefreshCw size={15} aria-hidden="true" /> : <QrCode size={15} aria-hidden="true" />}
                                  {token ? "QR’ı Yenile" : "QR Oluştur"}
                                </Button>
                                {token ? (
                                  <>
                                    <Button className={pageStyles.actionButton} size="md" variant="ghost" onClick={() => handleDownloadQr(table, token)}>
                                      <Download size={15} aria-hidden="true" />
                                      PNG İndir
                                    </Button>
                                    <Button className={pageStyles.actionButton} size="md" variant="ghost" onClick={() => handlePrintQr(table, token)}>
                                      <Printer size={15} aria-hidden="true" />
                                      Yazdır
                                    </Button>
                                    <Button
                                      className={pageStyles.revokeButton}
                                      size="md"
                                      variant="ghost"
                                      disabled={busyTableId === table.id}
                                      onClick={() => setRevokeTarget({ tableId: table.id, qrTokenId: token.id, label: table.label })}
                                    >
                                      <Ban size={15} aria-hidden="true" />
                                      QR’ı İptal Et
                                    </Button>
                                  </>
                                ) : null}
                                <Button
                                  className={pageStyles.revokeButton}
                                  size="md"
                                  variant="ghost"
                                  disabled={busyTableId === table.id}
                                  onClick={() => setArchiveTarget({ tableId: table.id, label: table.label })}
                                >
                                  <Archive size={15} aria-hidden="true" />
                                  Arşivle
                                </Button>
                                <Button
                                  className={pageStyles.revokeButton}
                                  size="md"
                                  variant="ghost"
                                  disabled={busyTableId === table.id}
                                  onClick={() => setDeleteTarget({ tableId: table.id, label: table.label })}
                                >
                                  <Trash2 size={15} aria-hidden="true" />
                                  Sil
                                </Button>
                              </>
                            ) : (
                              <Button
                                className={pageStyles.primaryButton}
                                size="md"
                                variant="primary"
                                disabled={busyTableId === table.id}
                                onClick={() => void handleReactivate(table.id)}
                              >
                                <RotateCcw size={15} aria-hidden="true" />
                                Tekrar Aktifleştir
                              </Button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </Table>
            )}
          </div>
        </section>
      </main>

      {createTableOpen ? (
        <Dialog onClose={() => setCreateTableOpen(false)} labelledBy={createTableDialogTitleId}>
          <h2 id={createTableDialogTitleId} className={styles.sectionTitle}>
            Yeni Masa
          </h2>
          <form className={styles.section} onSubmit={handleCreateTable}>
            <FormField label="Masa adı" required>
              {(controlProps) => <Input {...controlProps} value={label} onChange={(event) => setLabel(event.target.value)} required />}
            </FormField>
            <Button className={pageStyles.createSubmit} type="submit" disabled={creating}>
              <span>{creating ? "Masa ekleniyor…" : "Masa Ekle"}</span>
            </Button>
          </form>
        </Dialog>
      ) : null}

      {renameTarget ? (
        <Dialog onClose={() => setRenameTarget(null)} labelledBy={renameTableDialogTitleId}>
          <h2 id={renameTableDialogTitleId} className={styles.sectionTitle}>
            Masayı Yeniden Adlandır
          </h2>
          <form className={styles.section} onSubmit={handleRenameTable}>
            <FormField label="Masa adı" required>
              {(controlProps) => (
                <Input {...controlProps} value={renameLabel} onChange={(event) => setRenameLabel(event.target.value)} required />
              )}
            </FormField>
            <Button className={pageStyles.createSubmit} type="submit" disabled={renaming}>
              <span>{renaming ? "Kaydediliyor…" : "Kaydet"}</span>
            </Button>
          </form>
        </Dialog>
      ) : null}

      {refreshTarget ? (
        <ConfirmDialog
          title="QR Kodunu Yenile"
          message={`"${refreshTarget.label}" masasının mevcut QR kodu iptal edilip yenisi oluşturulacak. Eski fiziksel QR etiketi artık çalışmayacak.`}
          confirmLabel="QR’ı Yenile"
          tone="danger"
          confirmLoading={busyTableId === refreshTarget.tableId}
          onConfirm={handleConfirmRefresh}
          onCancel={() => setRefreshTarget(null)}
        />
      ) : null}

      {revokeTarget ? (
        <ConfirmDialog
          title="QR Kodunu İptal Et"
          message={`"${revokeTarget.label}" masasının QR kodu iptal edilecek. Bu masadaki fiziksel QR etiketi artık çalışmayacak.`}
          confirmLabel="QR’ı İptal Et"
          tone="danger"
          confirmLoading={busyTableId === revokeTarget.tableId}
          onConfirm={handleConfirmRevoke}
          onCancel={() => setRevokeTarget(null)}
        />
      ) : null}

      {archiveTarget ? (
        <ConfirmDialog
          title="Masayı Arşivle"
          message={`"${archiveTarget.label}" masası arşivlenecek: aktif QR kodu iptal edilir ve masa yeni QR/check-in kabul etmez. Geçmiş ziyaret ve sipariş kayıtları korunur, istenirse daha sonra tekrar aktifleştirilebilir.`}
          confirmLabel="Arşivle"
          tone="danger"
          confirmLoading={busyTableId === archiveTarget.tableId}
          onConfirm={handleConfirmArchive}
          onCancel={() => setArchiveTarget(null)}
        />
      ) : null}

      {deleteTarget ? (
        <ConfirmDialog
          title="Masayı Sil"
          message={`"${deleteTarget.label}" masası kalıcı olarak silinecek. Bu işlem geri alınamaz. (Bu masanın sipariş/ziyaret geçmişi varsa silme işlemi reddedilir; bunun yerine arşivleme kullanılmalıdır.)`}
          confirmLabel="Kalıcı Olarak Sil"
          tone="danger"
          confirmLoading={busyTableId === deleteTarget.tableId}
          onConfirm={handleConfirmDelete}
          onCancel={() => setDeleteTarget(null)}
        />
      ) : null}
    </AppShell>
  );
}
