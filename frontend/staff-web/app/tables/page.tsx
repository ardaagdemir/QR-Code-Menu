"use client";

import { useCallback, useEffect, useId, useMemo, useState } from "react";
import QRCode from "qrcode";
import JSZip from "jszip";
import jsPDF from "jspdf";
import {
  Download,
  FileText,
  Home,
  Pencil,
  Plus,
  QrCode,
  RefreshCw,
  RotateCcw,
  Table2,
  Trash2,
  Trees,
  X,
} from "lucide-react";
import {
  ApiError,
  bulkCreateTables,
  createTable,
  deleteTable,
  getActiveQrToken,
  listTables,
  reactivateTable,
  regenerateQrToken,
  updateTable,
  type QrToken,
  type StaffTable,
  type TableLocation,
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
import Select from "@/components/ui/Select";
import Table from "@/components/ui/Table";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Tabs from "@/components/ui/Tabs";
import { useToast } from "@/components/ui/ToastProvider";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";
import pageStyles from "./page.module.css";

const LOCATION_LABELS: Record<TableLocation, string> = { INDOOR: "İç Mekan", OUTDOOR: "Dış Mekan" };

function customerMenuUrl(token: string): string {
  const baseUrl = process.env.NEXT_PUBLIC_CUSTOMER_WEB_URL?.trim() || "http://localhost:3000";
  return `${baseUrl.replace(/\/$/, "")}/t/${encodeURIComponent(token)}`;
}

function safeFilename(value: string): string {
  return value.toLocaleLowerCase("tr-TR").replace(/[^a-z0-9çğıöşü]+/gi, "-").replace(/^-|-$/g, "") || "masa";
}

/** "" -> null, aksi halde parseInt - kapasite/sayı alanları boş bırakılabilir. */
function parseOptionalInt(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  const parsed = Number.parseInt(trimmed, 10);
  return Number.isFinite(parsed) ? parsed : null;
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("QR görseli yüklenemedi."));
    image.src = src;
  });
}

/** QR kodunun altına masa adını basar; hem tekli hem toplu indirmelerde baskıya hazır tek görsel elde etmek için kullanılır. */
async function composeLabeledQr(
  label: string,
  qrTargetUrl: string,
  qrSize: number,
  format: "png" | "jpeg" = "png",
): Promise<{ dataUrl: string; width: number; height: number }> {
  const qrDataUrl = await QRCode.toDataURL(qrTargetUrl, { width: qrSize, margin: 2 });
  const qrImage = await loadImage(qrDataUrl);
  const padding = Math.round(qrSize * 0.06);
  const labelHeight = Math.round(qrSize * 0.15);

  const canvas = document.createElement("canvas");
  canvas.width = qrImage.width + padding * 2;
  canvas.height = qrImage.height + padding * 2 + labelHeight;
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    throw new Error("Canvas desteklenmiyor.");
  }

  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(qrImage, padding, padding);

  ctx.fillStyle = "#1c1c1c";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  const maxTextWidth = canvas.width - padding * 1.5;
  let fontSize = Math.round(labelHeight * 0.55);
  ctx.font = `600 ${fontSize}px Arial, sans-serif`;
  while (fontSize > 14 && ctx.measureText(label).width > maxTextWidth) {
    fontSize -= 2;
    ctx.font = `600 ${fontSize}px Arial, sans-serif`;
  }
  ctx.fillText(label, canvas.width / 2, padding + qrImage.height + labelHeight / 2, maxTextWidth);

  const dataUrl =
    format === "jpeg" ? canvas.toDataURL("image/jpeg", 0.92) : canvas.toDataURL("image/png");
  return { dataUrl, width: canvas.width, height: canvas.height };
}

type EditTarget = { tableId: string; label: string; location: TableLocation; capacity: number | null };

export default function TablesPage() {
  const { showToast } = useToast();
  const createTableDialogTitleId = useId();
  const editTableDialogTitleId = useId();

  const [tables, setTables] = useState<StaffTable[]>([]);
  const [qrTokens, setQrTokens] = useState<Record<string, QrToken | null>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyTableId, setBusyTableId] = useState<string | null>(null);
  const [refreshTarget, setRefreshTarget] = useState<{ tableId: string; label: string } | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<{ tableId: string; label: string } | null>(null);

  const [createTableOpen, setCreateTableOpen] = useState(false);
  const [createMode, setCreateMode] = useState<"auto" | "manual">("auto");
  const [creating, setCreating] = useState(false);

  const [autoLocation, setAutoLocation] = useState<TableLocation>("INDOOR");
  const [autoCount, setAutoCount] = useState("5");
  const [autoPrefix, setAutoPrefix] = useState("Masa");
  const [autoCapacity, setAutoCapacity] = useState("");

  const [manualLabel, setManualLabel] = useState("");
  const [manualLocation, setManualLocation] = useState<TableLocation>("INDOOR");
  const [manualCapacity, setManualCapacity] = useState("");

  const [editTarget, setEditTarget] = useState<EditTarget | null>(null);
  const [editLabel, setEditLabel] = useState("");
  const [editLocation, setEditLocation] = useState<TableLocation>("INDOOR");
  const [editCapacity, setEditCapacity] = useState("");
  const [editing, setEditing] = useState(false);

  const [selectedTableIds, setSelectedTableIds] = useState<Set<string>>(new Set());
  const [bulkDownloading, setBulkDownloading] = useState(false);

  const selectedCount = useMemo(
    () => tables.filter((table) => selectedTableIds.has(table.id) && qrTokens[table.id]).length,
    [tables, selectedTableIds, qrTokens],
  );

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

  function closeCreateDialog() {
    setCreateTableOpen(false);
    setCreateMode("auto");
    setAutoLocation("INDOOR");
    setAutoCount("5");
    setAutoPrefix("Masa");
    setAutoCapacity("");
    setManualLabel("");
    setManualLocation("INDOOR");
    setManualCapacity("");
  }

  async function handleCreateAuto(event: React.FormEvent) {
    event.preventDefault();
    const count = Number.parseInt(autoCount, 10);
    if (!Number.isFinite(count) || count < 1) {
      return;
    }
    setCreating(true);
    try {
      const created = await bulkCreateTables(autoLocation, count, autoPrefix.trim() || "Masa", parseOptionalInt(autoCapacity));
      closeCreateDialog();
      await loadTables();
      showToast(`${created.length} masa oluşturuldu.`, "success");
    } catch {
      showToast("Masalar oluşturulamadı.", "error");
    } finally {
      setCreating(false);
    }
  }

  async function handleCreateManual(event: React.FormEvent) {
    event.preventDefault();
    if (!manualLabel.trim()) {
      return;
    }
    setCreating(true);
    try {
      await createTable(manualLabel.trim(), manualLocation, parseOptionalInt(manualCapacity));
      closeCreateDialog();
      await loadTables();
      showToast("Masa oluşturuldu.", "success");
    } catch {
      showToast("Masa oluşturulamadı.", "error");
    } finally {
      setCreating(false);
    }
  }

  function openEditDialog(table: StaffTable) {
    setEditTarget({ tableId: table.id, label: table.label, location: table.location, capacity: table.capacity });
    setEditLabel(table.label);
    setEditLocation(table.location);
    setEditCapacity(table.capacity != null ? String(table.capacity) : "");
  }

  async function handleSaveEdit(event: React.FormEvent) {
    event.preventDefault();
    if (!editTarget || !editLabel.trim()) {
      return;
    }
    setEditing(true);
    try {
      const updated = await updateTable(editTarget.tableId, editLabel.trim(), editLocation, parseOptionalInt(editCapacity));
      setTables((current) => current.map((table) => (table.id === updated.id ? updated : table)));
      setEditTarget(null);
      showToast("Masa güncellendi.", "success");
    } catch {
      showToast("Masa güncellenemedi.", "error");
    } finally {
      setEditing(false);
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
      setSelectedTableIds((current) => {
        if (!current.has(deleteTarget.tableId)) {
          return current;
        }
        const next = new Set(current);
        next.delete(deleteTarget.tableId);
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
      const { dataUrl } = await composeLabeledQr(table.label, customerMenuUrl(token.token), 1024);
      const link = document.createElement("a");
      link.href = dataUrl;
      link.download = `${safeFilename(table.label)}-qr.png`;
      link.click();
    } catch {
      showToast("QR kod indirilemedi.", "error");
    }
  }

  function toggleTableSelection(tableId: string) {
    setSelectedTableIds((current) => {
      const next = new Set(current);
      if (next.has(tableId)) {
        next.delete(tableId);
      } else {
        next.add(tableId);
      }
      return next;
    });
  }

  function toggleLocationSelection(location: TableLocation, tableIds: string[]) {
    setSelectedTableIds((current) => {
      const next = new Set(current);
      const allSelected = tableIds.every((id) => next.has(id));
      tableIds.forEach((id) => (allSelected ? next.delete(id) : next.add(id)));
      return next;
    });
  }

  function clearSelection() {
    setSelectedTableIds(new Set());
  }

  function getSelectedTablesWithTokens(): Array<{ table: StaffTable; token: QrToken }> {
    return tables
      .filter((table) => selectedTableIds.has(table.id) && qrTokens[table.id])
      .map((table) => ({ table, token: qrTokens[table.id] as QrToken }));
  }

  async function handleBulkDownloadZip() {
    const selected = getSelectedTablesWithTokens();
    if (selected.length === 0) {
      return;
    }
    setBulkDownloading(true);
    try {
      const zip = new JSZip();
      const usedNames = new Set<string>();
      for (const { table, token } of selected) {
        const { dataUrl } = await composeLabeledQr(table.label, customerMenuUrl(token.token), 900);
        const base64 = dataUrl.slice(dataUrl.indexOf(",") + 1);
        let name = `${safeFilename(table.label)}-qr.png`;
        let suffix = 2;
        while (usedNames.has(name)) {
          name = `${safeFilename(table.label)}-qr-${suffix}.png`;
          suffix += 1;
        }
        usedNames.add(name);
        zip.file(name, base64, { base64: true });
      }
      const blob = await zip.generateAsync({ type: "blob" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `masa-qr-kodlari-${new Date().toISOString().slice(0, 10)}.zip`;
      link.click();
      URL.revokeObjectURL(url);
      showToast(`${selected.length} QR kod indirildi.`, "success");
      clearSelection();
    } catch {
      showToast("QR kodları indirilemedi.", "error");
    } finally {
      setBulkDownloading(false);
    }
  }

  async function handleBulkDownloadPdf() {
    const selected = getSelectedTablesWithTokens();
    if (selected.length === 0) {
      return;
    }
    setBulkDownloading(true);
    try {
      const margin = 12;
      const cols = 2;
      const rows = 3;
      const pageWidth = 210;
      const pageHeight = 297;
      const cellWidth = (pageWidth - margin * 2) / cols;
      const cellHeight = (pageHeight - margin * 2) / rows;
      const cellPadding = 8;
      const perPage = cols * rows;

      const doc = new jsPDF({ unit: "mm", format: "a4" });
      for (let index = 0; index < selected.length; index += 1) {
        const { table, token } = selected[index];
        if (index > 0 && index % perPage === 0) {
          doc.addPage();
        }
        const { dataUrl, width, height } = await composeLabeledQr(
          table.label,
          customerMenuUrl(token.token),
          640,
          "jpeg",
        );

        const positionInPage = index % perPage;
        const col = positionInPage % cols;
        const row = Math.floor(positionInPage / cols);
        const x0 = margin + col * cellWidth;
        const y0 = margin + row * cellHeight;

        doc.setDrawColor(210);
        doc.rect(x0 + 2, y0 + 2, cellWidth - 4, cellHeight - 4);

        const boxWidth = cellWidth - cellPadding * 2;
        const boxHeight = cellHeight - cellPadding * 2;
        const aspectRatio = width / height;
        let drawWidth = boxWidth;
        let drawHeight = drawWidth / aspectRatio;
        if (drawHeight > boxHeight) {
          drawHeight = boxHeight;
          drawWidth = drawHeight * aspectRatio;
        }
        const dx = x0 + (cellWidth - drawWidth) / 2;
        const dy = y0 + (cellHeight - drawHeight) / 2;
        doc.addImage(dataUrl, "JPEG", dx, dy, drawWidth, drawHeight);
      }
      doc.save(`masa-qr-kodlari-${new Date().toISOString().slice(0, 10)}.pdf`);
      showToast(`${selected.length} QR kod PDF olarak indirildi.`, "success");
      clearSelection();
    } catch {
      showToast("PDF oluşturulamadı.", "error");
    } finally {
      setBulkDownloading(false);
    }
  }

  function renderLocationPanel(location: TableLocation) {
    const locationTables = tables.filter((table) => table.location === location);
    if (locationTables.length === 0) {
      return null;
    }
    const downloadableIds = locationTables.filter((table) => table.active && qrTokens[table.id]).map((table) => table.id);
    const allDownloadableSelected =
      downloadableIds.length > 0 && downloadableIds.every((id) => selectedTableIds.has(id));
    const Icon = location === "INDOOR" ? Home : Trees;
    return (
      <section key={location} className={pageStyles.tablePanel}>
        <div className={pageStyles.panelHeader}>
          <div>
            <h2 className={pageStyles.panelTitle}>
              <Icon size={16} aria-hidden="true" className={pageStyles.panelTitleIcon} />
              {LOCATION_LABELS[location]}
            </h2>
            <p className={pageStyles.panelDescription}>Masa QR durumlarını tek ekrandan yönetin.</p>
          </div>
          <span className={pageStyles.tableCount}>{locationTables.length} masa</span>
        </div>

        <div className={pageStyles.panelBody}>
          <Table>
            <thead>
              <tr>
                <th className={pageStyles.checkboxCell}>
                  <input
                    type="checkbox"
                    aria-label={`${LOCATION_LABELS[location]} bölümündeki tüm QR'lı masaları seç`}
                    checked={allDownloadableSelected}
                    disabled={downloadableIds.length === 0}
                    onChange={() => toggleLocationSelection(location, downloadableIds)}
                  />
                </th>
                <th>Masa</th>
                <th>Kapasite</th>
                <th>QR Durumu</th>
                <th aria-label="QR işlemleri" />
              </tr>
            </thead>
            <tbody>
              {locationTables.map((table) => {
                const token = qrTokens[table.id];
                return (
                  <tr key={table.id}>
                    <td className={pageStyles.checkboxCell}>
                      <input
                        type="checkbox"
                        aria-label={`"${table.label}" masasını seç`}
                        checked={selectedTableIds.has(table.id)}
                        disabled={!table.active || !token}
                        onChange={() => toggleTableSelection(table.id)}
                      />
                    </td>
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
                          aria-label={`"${table.label}" masasını düzenle`}
                          onClick={() => openEditDialog(table)}
                        >
                          <Pencil size={14} aria-hidden="true" />
                        </Button>
                      </div>
                    </td>
                    <td>{table.capacity ?? "—"}</td>
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
                      <div className={`${tableStyles.actions} ${pageStyles.actions}`}>
                        {table.active ? (
                          <>
                            <Button
                              className={token ? undefined : pageStyles.primaryButton}
                              size="sm"
                              variant={token ? "accent" : "primary"}
                              disabled={busyTableId === table.id}
                              onClick={() =>
                                token
                                  ? setRefreshTarget({ tableId: table.id, label: table.label })
                                  : void handleRegenerate(table.id)
                              }
                            >
                              {token ? <RefreshCw size={13} aria-hidden="true" /> : <QrCode size={13} aria-hidden="true" />}
                              {token ? "QR’ı Yenile" : "QR Oluştur"}
                            </Button>
                            {token ? (
                              <Button size="sm" variant="secondary" onClick={() => handleDownloadQr(table, token)}>
                                <Download size={13} aria-hidden="true" />
                                PNG İndir
                              </Button>
                            ) : null}
                            <Button
                              size="sm"
                              variant="danger"
                              disabled={busyTableId === table.id}
                              onClick={() => setDeleteTarget({ tableId: table.id, label: table.label })}
                            >
                              <Trash2 size={13} aria-hidden="true" />
                              Sil
                            </Button>
                          </>
                        ) : (
                          <Button
                            className={pageStyles.primaryButton}
                            size="sm"
                            variant="primary"
                            disabled={busyTableId === table.id}
                            onClick={() => void handleReactivate(table.id)}
                          >
                            <RotateCcw size={13} aria-hidden="true" />
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
        </div>
      </section>
    );
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

        {loading ? (
          <div className={pageStyles.tablePanel}>
            <TableSkeleton />
          </div>
        ) : error ? (
          <div className={pageStyles.tablePanel}>
            <ErrorState
              message={error}
              onRetry={() => {
                setLoading(true);
                void loadTables();
              }}
            />
          </div>
        ) : tables.length === 0 ? (
          <div className={pageStyles.tablePanel}>
            <EmptyState title="Henüz masa yok" description="Aktif şubeye ilk masayı ekleyin." />
          </div>
        ) : (
          <div className={pageStyles.panelStack}>
            {selectedCount > 0 ? (
              <div className={pageStyles.bulkBar}>
                <span className={pageStyles.bulkBarText}>{selectedCount} masa seçildi</span>
                <div className={pageStyles.bulkBarActions}>
                  <Button size="sm" variant="ghost" onClick={clearSelection} disabled={bulkDownloading}>
                    <X size={13} aria-hidden="true" />
                    Seçimi Temizle
                  </Button>
                  <Button size="sm" variant="secondary" onClick={() => void handleBulkDownloadZip()} disabled={bulkDownloading}>
                    <Download size={13} aria-hidden="true" />
                    {bulkDownloading ? "İndiriliyor…" : `PNG İndir (${selectedCount})`}
                  </Button>
                  <Button
                    className={pageStyles.primaryButton}
                    size="sm"
                    onClick={() => void handleBulkDownloadPdf()}
                    disabled={bulkDownloading}
                  >
                    <FileText size={13} aria-hidden="true" />
                    {bulkDownloading ? "İndiriliyor…" : `PDF İndir (${selectedCount})`}
                  </Button>
                </div>
              </div>
            ) : null}
            {renderLocationPanel("INDOOR")}
            {renderLocationPanel("OUTDOOR")}
          </div>
        )}
      </main>

      {createTableOpen ? (
        <Dialog onClose={closeCreateDialog} labelledBy={createTableDialogTitleId}>
          <h2 id={createTableDialogTitleId} className={styles.sectionTitle}>
            Yeni Masa
          </h2>
          <div className={pageStyles.createModeTabs}>
            <Tabs
              items={[
                { id: "auto", label: "Otomatik Oluştur" },
                { id: "manual", label: "Manuel Oluştur" },
              ]}
              activeId={createMode}
              onChange={(id) => setCreateMode(id as "auto" | "manual")}
              ariaLabel="Masa oluşturma yöntemi"
            />
          </div>

          {createMode === "auto" ? (
            <form className={styles.section} onSubmit={handleCreateAuto}>
              <FormField label="Konum" required>
                {(controlProps) => (
                  <Select
                    {...controlProps}
                    value={autoLocation}
                    onChange={(event) => setAutoLocation(event.target.value as TableLocation)}
                  >
                    <option value="INDOOR">İç Mekan</option>
                    <option value="OUTDOOR">Dış Mekan</option>
                  </Select>
                )}
              </FormField>
              <FormField label="Masa sayısı" required>
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    type="number"
                    min={1}
                    max={100}
                    value={autoCount}
                    onChange={(event) => setAutoCount(event.target.value)}
                    required
                  />
                )}
              </FormField>
              <FormField label="İsim prefix'i" hint='Örn. "Masa" girilirse Masa 1, Masa 2 ... oluşturulur.'>
                {(controlProps) => (
                  <Input {...controlProps} value={autoPrefix} onChange={(event) => setAutoPrefix(event.target.value)} />
                )}
              </FormField>
              <FormField label="Kapasite" hint="Opsiyonel - girilirse tüm masalara uygulanır.">
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    type="number"
                    min={1}
                    value={autoCapacity}
                    onChange={(event) => setAutoCapacity(event.target.value)}
                  />
                )}
              </FormField>
              <Button className={pageStyles.createSubmit} type="submit" disabled={creating}>
                <span>{creating ? "Masalar ekleniyor…" : "Masaları Oluştur"}</span>
              </Button>
            </form>
          ) : (
            <form className={styles.section} onSubmit={handleCreateManual}>
              <FormField label="Masa adı" required>
                {(controlProps) => (
                  <Input {...controlProps} value={manualLabel} onChange={(event) => setManualLabel(event.target.value)} required />
                )}
              </FormField>
              <FormField label="Kapasite" hint="Opsiyonel.">
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    type="number"
                    min={1}
                    value={manualCapacity}
                    onChange={(event) => setManualCapacity(event.target.value)}
                  />
                )}
              </FormField>
              <FormField label="Konum" required>
                {(controlProps) => (
                  <Select
                    {...controlProps}
                    value={manualLocation}
                    onChange={(event) => setManualLocation(event.target.value as TableLocation)}
                  >
                    <option value="INDOOR">İç Mekan</option>
                    <option value="OUTDOOR">Dış Mekan</option>
                  </Select>
                )}
              </FormField>
              <Button className={pageStyles.createSubmit} type="submit" disabled={creating}>
                <span>{creating ? "Masa ekleniyor…" : "Masa Ekle"}</span>
              </Button>
            </form>
          )}
        </Dialog>
      ) : null}

      {editTarget ? (
        <Dialog onClose={() => setEditTarget(null)} labelledBy={editTableDialogTitleId}>
          <h2 id={editTableDialogTitleId} className={styles.sectionTitle}>
            Masayı Düzenle
          </h2>
          <form className={styles.section} onSubmit={handleSaveEdit}>
            <FormField label="Masa adı" required>
              {(controlProps) => (
                <Input {...controlProps} value={editLabel} onChange={(event) => setEditLabel(event.target.value)} required />
              )}
            </FormField>
            <FormField label="Kapasite" hint="Opsiyonel.">
              {(controlProps) => (
                <Input
                  {...controlProps}
                  type="number"
                  min={1}
                  value={editCapacity}
                  onChange={(event) => setEditCapacity(event.target.value)}
                />
              )}
            </FormField>
            <FormField label="Konum" required>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  value={editLocation}
                  onChange={(event) => setEditLocation(event.target.value as TableLocation)}
                >
                  <option value="INDOOR">İç Mekan</option>
                  <option value="OUTDOOR">Dış Mekan</option>
                </Select>
              )}
            </FormField>
            <Button className={pageStyles.createSubmit} type="submit" disabled={editing}>
              <span>{editing ? "Kaydediliyor…" : "Kaydet"}</span>
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
