"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { me, type StaffContext } from "@/lib/api";
import { NAV_GROUPS, ROLE_LABELS } from "@/lib/staffNav";
import AppShell from "@/components/layout/AppShell";
import Card from "@/components/ui/Card";
import Skeleton from "@/components/ui/Skeleton";
import adminStyles from "@/styles/admin.module.css";
import styles from "./page.module.css";

/**
 * Bölüm 19.3 "Dashboard" - minimal placeholder (karşılama + role göre kısayollar).
 * Gerçek KPI içeriği (bugünkü ciro, sipariş sayısı, en çok satan ürünler vb.) UI/UX
 * Productization Gate Adım 6'da (raporlama/dashboard görselleştirme) eklenecek; bu
 * sayfa yalnızca login sonrası landing + AppShell'in "Dashboard" linkinin hedefi.
 * Kısayol kartları NAV_GROUPS'un aynısını kullanır (Dashboard hariç) - link/rol/href
 * mantığı tek yerde (lib/staffNav.ts) kalır.
 */
export default function DashboardPage() {
  const [context, setContext] = useState<StaffContext | null>(null);

  useEffect(() => {
    me()
      .then(setContext)
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
          shortcutGroups.map((group) => (
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
          ))
        )}
      </main>
    </AppShell>
  );
}
