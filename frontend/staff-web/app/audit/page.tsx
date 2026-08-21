"use client";

import { useEffect, useState } from "react";
import { listAuditEntries, listStaffUsers, type AuditEntry, type StaffUser } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import Table from "@/components/ui/Table";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import Badge from "@/components/ui/Badge";
import tableStyles from "@/components/ui/Table.module.css";
import styles from "@/styles/admin.module.css";
import pageStyles from "./page.module.css";

const ENTITY_LABELS: Record<string, string> = {
  Business: "İşletme",
  Branch: "Şube",
  TableQrToken: "Masa QR kodu",
  BusinessContact: "Rapor alıcısı",
  MenuCategory: "Menü kategorisi",
  Product: "Ürün",
  ProductOptionGroup: "Ürün seçenek grubu",
  ProductOption: "Ürün seçeneği",
  BranchProduct: "Şube ürünü",
  ExpenseCategory: "Gider kategorisi",
  Expense: "Gider",
  RecurringExpenseTemplate: "Tekrarlayan gider",
  Order: "Sipariş",
  Refund: "İade",
};

const ACTION_LABELS: Record<string, string> = {
  CREATED: "Oluşturuldu",
  UPDATED: "Güncellendi",
  ACTIVATED: "Aktifleştirildi",
  DEACTIVATED: "Devre dışı bırakıldı",
  DELETED: "Silindi",
  ACCEPTED_BY_STORE: "Kabul edildi",
  REJECTED_BY_STORE: "Reddedildi",
  ISSUED: "Gerçekleştirildi",
  REVOKED: "İptal edildi",
  UPSERTED: "Şube durumu güncellendi",
  SETTINGS_CHANGED: "Ayarlar güncellendi",
  ORDERING_TOGGLED: "Sipariş alımı değiştirildi",
  DELIVERY_MODEL_CHANGED: "Teslimat modeli değiştirildi",
  ADDRESS_CHANGED: "Adres değiştirildi",
  TIMEZONE_CHANGED: "Saat dilimi değiştirildi",
  STORE_ACCEPTANCE_TIMEOUT_CHANGED: "Kabul süresi değiştirildi",
  BUSINESS_HOURS_CHANGED: "Çalışma saatleri değiştirildi",
  ENDED: "Sonlandırıldı",
};

const REJECTION_REASON_LABELS: Record<string, string> = {
  OUT_OF_STOCK: "Ürün tükendi",
  KITCHEN_BUSY: "Mutfak yoğunluğu",
  CLOSED: "Şube kapalı",
  OTHER: "Diğer",
};

type AuditDetails = Record<string, unknown>;

function parseDetails(details: string | null): AuditDetails {
  if (!details) return {};
  try {
    const parsed: unknown = JSON.parse(details);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed as AuditDetails : {};
  } catch {
    return {};
  }
}

function detailString(details: AuditDetails, key: string): string | null {
  const value = details[key];
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function detailNumber(details: AuditDetails, key: string): number | null {
  const value = details[key];
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function detailBoolean(details: AuditDetails, key: string): boolean | null {
  const value = details[key];
  if (typeof value === "boolean") return value;
  if (value === "true") return true;
  if (value === "false") return false;
  return null;
}

function formatAmount(minorUnits: number): string {
  return new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(minorUnits / 100);
}

function entityLabel(entityType: string): string {
  return ENTITY_LABELS[entityType] ?? "Kayıt";
}

function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? "İşlem yapıldı";
}

function actionTone(action: string): "neutral" | "success" | "danger" | "warning" | "info" {
  if (["CREATED", "ACTIVATED", "ACCEPTED_BY_STORE", "ISSUED"].includes(action)) return "success";
  if (["DEACTIVATED", "DELETED", "REJECTED_BY_STORE", "REVOKED"].includes(action)) return "danger";
  if (["ORDERING_TOGGLED", "DELIVERY_MODEL_CHANGED", "STORE_ACCEPTANCE_TIMEOUT_CHANGED"].includes(action)) return "warning";
  if (["UPDATED", "UPSERTED", "SETTINGS_CHANGED", "ADDRESS_CHANGED", "TIMEZONE_CHANGED", "BUSINESS_HOURS_CHANGED"].includes(action)) return "info";
  return "neutral";
}

function auditDescription(entry: AuditEntry): string {
  const details = parseDetails(entry.details);
  const label = entityLabel(entry.entityType);
  const name = detailString(details, "name");
  const amount = detailNumber(details, "amountMinorUnits");

  if (entry.action === "CREATED") {
    if (name) return `${label} “${name}” oluşturuldu.`;
    if (amount !== null) return `${formatAmount(amount)} tutarında ${label.toLocaleLowerCase("tr-TR")} oluşturuldu.`;
    return `${label} oluşturuldu.`;
  }

  if (entry.action === "UPDATED") {
    const active = detailBoolean(details, "active");
    const dayOfMonth = detailNumber(details, "dayOfMonth");
    if (amount !== null && dayOfMonth !== null) {
      return `Tutar ${formatAmount(amount)}, tekrar günü ayın ${dayOfMonth}. günü olarak güncellendi.`;
    }
    if (active !== null) return `${label} ${active ? "aktif" : "pasif"} duruma getirildi.`;
    return `${label} bilgileri güncellendi.`;
  }

  if (entry.action === "DEACTIVATED") return `${label} devre dışı bırakıldı.`;
  if (entry.action === "ACTIVATED") return `${label} aktifleştirildi.`;
  if (entry.action === "DELETED") return `${label} silindi; geçmiş kayıtlar korundu.`;
  if (entry.action === "ACCEPTED_BY_STORE") return "Sipariş şube tarafından kabul edildi.";
  if (entry.action === "REJECTED_BY_STORE") {
    const reasonCode = detailString(details, "reasonCode");
    const reason = reasonCode ? REJECTION_REASON_LABELS[reasonCode] ?? "Belirtilen neden" : "Belirtilen neden";
    return `Sipariş şube tarafından reddedildi. Neden: ${reason}.`;
  }
  if (entry.action === "ISSUED") {
    const totalAmount = detailNumber(details, "totalAmountMinorUnits");
    return totalAmount !== null ? `${formatAmount(totalAmount)} tutarında iade gerçekleştirildi.` : "İade gerçekleştirildi.";
  }
  if (entry.action === "REVOKED") return "Masa QR kodu iptal edildi.";
  if (entry.action === "UPSERTED") {
    const availability = detailString(details, "availability");
    return availability === "AVAILABLE" ? "Ürün şubede satışa açıldı." : availability === "UNAVAILABLE" ? "Ürün şubede satıştan kaldırıldı." : "Ürünün şube durumu güncellendi.";
  }
  if (entry.action === "SETTINGS_CHANGED") return "İşletmenin para birimi ve saat dilimi ayarları güncellendi.";
  if (entry.action === "ORDERING_TOGGLED") {
    const enabled = detailBoolean(details, "orderingEnabled");
    return enabled === null ? "Şubenin sipariş alım durumu değiştirildi." : `Şubede sipariş alımı ${enabled ? "açıldı" : "kapatıldı"}.`;
  }
  if (entry.action === "DELIVERY_MODEL_CHANGED") {
    const deliveryModel = detailString(details, "deliveryModel");
    const deliveryLabel = deliveryModel === "WAITER_DELIVERY" ? "Garson servisi" : deliveryModel === "CUSTOMER_PICKUP" ? "Müşteri teslim alır" : null;
    return deliveryLabel ? `Teslimat modeli “${deliveryLabel}” olarak güncellendi.` : "Şubenin teslimat modeli güncellendi.";
  }
  if (entry.action === "ADDRESS_CHANGED") return "Şube adresi güncellendi.";
  if (entry.action === "TIMEZONE_CHANGED") return "Şube saat dilimi güncellendi.";
  if (entry.action === "STORE_ACCEPTANCE_TIMEOUT_CHANGED") {
    const seconds = detailNumber(details, "timeoutSeconds");
    return seconds !== null ? `Kasa kabul süresi ${seconds % 60 === 0 ? `${seconds / 60} dakika` : `${seconds} saniye`} olarak güncellendi.` : "Kasa kabul süresi güncellendi.";
  }
  if (entry.action === "BUSINESS_HOURS_CHANGED") {
    const dayCount = detailNumber(details, "dayCount");
    return dayCount !== null ? `${dayCount} günün çalışma saatleri güncellendi.` : "Şubenin çalışma saatleri güncellendi.";
  }

  return `${label} üzerinde işlem yapıldı.`;
}

function formatAuditDate(value: string): string {
  return new Date(value).toLocaleString("tr-TR", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

/** Audit entries returned by the backend are restricted to the active branch. */
export default function AuditPage() {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [staffUsers, setStaffUsers] = useState<StaffUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  function load() {
    Promise.all([listAuditEntries(), listStaffUsers()])
      .then(([auditEntries, users]) => {
        setEntries(auditEntries);
        setStaffUsers(users);
        setError(null);
      })
      .catch(() => setError("Denetim kaydı yüklenemedi."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  function actorLabel(actorStaffUserId: string | null): string {
    if (!actorStaffUserId) return "Sistem";
    return staffUsers.find((user) => user.id === actorStaffUserId)?.email ?? "Bilinmeyen kullanıcı";
  }

  return (
    <AppShell>
      <main className={styles.page}>
        <PageHeader title="Denetim Kaydı" description="Aktif şubenizdeki kritik işlemlerin denetim izi." />
        {loading ? <TableSkeleton /> : error ? (
          <ErrorState message={error} onRetry={load} />
        ) : entries.length === 0 ? (
          <EmptyState title="Henüz kayıt yok" />
        ) : (
          <div className={pageStyles.auditTable}>
            <Table>
              <thead><tr><th>Tarih</th><th>Kullanıcı</th><th>İşlem</th><th>Açıklama</th></tr></thead>
              <tbody>
                {entries.map((entry) => (
                  <tr key={entry.id}>
                    <td className={`${tableStyles.muted} ${pageStyles.dateCell}`}>{formatAuditDate(entry.createdAt)}</td>
                    <td className={tableStyles.primary}>{actorLabel(entry.actorStaffUserId)}</td>
                    <td>
                      <div className={pageStyles.operationCell}>
                        <span className={pageStyles.entityLabel}>{entityLabel(entry.entityType)}</span>
                        <Badge tone={actionTone(entry.action)}>{actionLabel(entry.action)}</Badge>
                      </div>
                    </td>
                    <td className={`${tableStyles.muted} ${pageStyles.descriptionCell}`}>{auditDescription(entry)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </div>
        )}
      </main>
    </AppShell>
  );
}
