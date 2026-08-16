"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  formatPriceMinorUnits,
  getBranchSalesReport,
  getPendingAcceptanceOrders,
  me,
  type BranchSalesReport,
  type StaffContext,
} from "@/lib/api";
import { NAV_GROUPS, ROLE_LABELS } from "@/lib/staffNav";
import AppShell from "@/components/layout/AppShell";
import Card from "@/components/ui/Card";
import KpiCard from "@/components/ui/KpiCard";
import BarList from "@/components/ui/BarList";
import Skeleton from "@/components/ui/Skeleton";
import adminStyles from "@/styles/admin.module.css";
import styles from "./page.module.css";

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

/** Permission.REPORT_VIEW (StaffRole.java) - every business role has report visibility. */
const REPORT_ROLES: StaffContext["role"][] = ["PLATFORM_ADMIN", "BUSINESS_ADMIN", "BRANCH_MANAGER", "CASHIER"];

/**
 * Bölüm 19.3 "Dashboard": login sonrası rolün kullanım amacına uygun landing page -
 * BUSINESS_ADMIN/BRANCH_MANAGER için bugünkü brüt/net satış, sipariş sayısı, ortalama
 * sepet, refund özeti, en çok satan ürünler ve aktif/bekleyen operasyon bilgisi.
 * Her kullanıcı yalnız session/context'ten çözülen aktif şubesinin raporunu görür;
 * ilk ekranda yalnızca "bugün" (from=to=today) - tarih aralığı seçimi /reports'ta.
 */
export default function DashboardPage() {
  const [context, setContext] = useState<StaffContext | null>(null);
  const [branchReport, setBranchReport] = useState<BranchSalesReport | null>(null);
  const [pendingCount, setPendingCount] = useState<number | null>(null);
  const [reportsLoading, setReportsLoading] = useState(false);

  useEffect(() => {
    me()
      .then((staffContext) => {
        setContext(staffContext);
        if (!REPORT_ROLES.includes(staffContext.role)) {
          return;
        }
        setReportsLoading(true);
        const today = todayIsoDate();
        if (staffContext.activeBranchId) {
          getBranchSalesReport(today, today)
            .then(setBranchReport)
            .catch(() => undefined)
            .finally(() => setReportsLoading(false));
          getPendingAcceptanceOrders()
            .then((orders) => setPendingCount(orders.length))
            .catch(() => undefined);
        } else {
          setReportsLoading(false);
        }
      })
      .catch(() => undefined);
  }, []);

  const shortcutGroups = context
    ? NAV_GROUPS.map((group) => ({
        title: group.title,
        items: group.items
          .filter((item) => item.key !== "dashboard" && item.roles.includes(context.role))
          .map((item) => ({ key: item.key, label: item.label, href: item.href(context) }))
          .filter((item): item is { key: string; label: string; href: string } => item.href !== null),
      })).filter((group) => group.items.length > 0)
    : [];

  const topProducts = branchReport
    ? [...branchReport.productBreakdown].sort((a, b) => b.revenueMinorUnits - a.revenueMinorUnits).slice(0, 5)
    : [];

  return (
    <AppShell>
      <main className={adminStyles.page}>
        <div className={adminStyles.header}>
          <h1 className={adminStyles.title}>
            {context ? `Hoş geldin, ${ROLE_LABELS[context.role] ?? context.role}` : "Hoş geldin"}
          </h1>
        </div>

        {!context ? (
          <div className={styles.grid}>
            <Skeleton height="96px" />
            <Skeleton height="96px" />
            <Skeleton height="96px" />
          </div>
        ) : (
          <>
            {REPORT_ROLES.includes(context.role) ? (
              <section className={adminStyles.section}>
                <h2 className={adminStyles.sectionTitle}>Bugün</h2>
                {reportsLoading ? (
                  <div className={styles.grid}>
                    <Skeleton height="88px" />
                    <Skeleton height="88px" />
                    <Skeleton height="88px" />
                    <Skeleton height="88px" />
                  </div>
                ) : branchReport ? (
                  <>
                    <div className={styles.kpiGrid}>
                      <KpiCard label="Brüt satış" value={formatPriceMinorUnits(branchReport.grossSalesMinorUnits)} />
                      <KpiCard label="Net satış" value={formatPriceMinorUnits(branchReport.netSalesMinorUnits)} />
                      <KpiCard
                        label="Refund toplamı"
                        value={formatPriceMinorUnits(branchReport.refundTotalMinorUnits)}
                        tone={branchReport.refundTotalMinorUnits > 0 ? "danger" : "neutral"}
                      />
                      <KpiCard label="Sipariş sayısı" value={String(branchReport.orderCount)} />
                      <KpiCard label="Ortalama sepet" value={formatPriceMinorUnits(branchReport.averageOrderValueMinorUnits)} />
                      {pendingCount !== null ? (
                        <KpiCard
                          label="Onay bekleyen sipariş"
                          value={String(pendingCount)}
                          tone={pendingCount > 0 ? "danger" : "success"}
                        />
                      ) : null}
                    </div>
                    {topProducts.length > 0 ? (
                      <div className={styles.subsection}>
                        <h3 className={styles.subsectionTitle}>En Çok Satan Ürünler</h3>
                        <BarList
                          items={topProducts.map((row) => ({
                            key: row.productId,
                            label: row.productName,
                            value: row.revenueMinorUnits,
                            valueLabel: `${formatPriceMinorUnits(row.revenueMinorUnits)} · ${row.quantitySold} adet`,
                          }))}
                        />
                      </div>
                    ) : null}
                    <Link href="/reports" className={styles.moreLink}>
                      Şube raporunu gör →
                    </Link>
                  </>
                ) : (
                  <p className={adminStyles.empty}>Bugün için henüz veri yok.</p>
                )}
              </section>
            ) : null}

            {shortcutGroups.map((group) => (
              <div key={group.title} className={adminStyles.section}>
                <h2 className={adminStyles.sectionTitle}>{group.title}</h2>
                <div className={styles.grid}>
                  {group.items.map((item) => (
                    <Link key={item.key} href={item.href} className={styles.cardLink}>
                      <Card className={styles.card}>{item.label}</Card>
                    </Link>
                  ))}
                </div>
              </div>
            ))}
          </>
        )}
      </main>
    </AppShell>
  );
}
