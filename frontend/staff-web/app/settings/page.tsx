"use client";

import { useEffect, useState } from "react";
import { me, type StaffContext } from "@/lib/api";
import AppShell from "@/components/layout/AppShell";
import PageHeader from "@/components/ui/PageHeader";
import Tabs from "@/components/ui/Tabs";
import TableSkeleton from "@/components/ui/TableSkeleton";
import BusinessTab from "./BusinessTab";
import BranchTab from "./BranchTab";
import styles from "@/styles/admin.module.css";
import pageStyles from "./page.module.css";

type TabId = "isletme" | "sube";

const TAB_ITEMS = [
  { id: "isletme", label: "İşletme" },
  { id: "sube", label: "Şube" },
];

/**
 * Eski /business-settings + /branches ekranlarının birleşimi. Tab görünürlüğü backend
 * permission mantığıyla tutarlı: yalnız BUSINESS_ADMIN her iki sekmeyi de görür (varsayılan
 * "İşletme"); BRANCH_MANAGER (yalnız Permission.ORDERING_TOGGLE) hiç tab çubuğu görmeden
 * doğrudan Şube içeriğini görür - İşletme sekmesine hiç erişimi yok. Diğer roller (CASHIER,
 * PLATFORM_ADMIN) normalde nav'da bu linki hiç görmez; doğrudan URL'e girerlerse Şube
 * içeriğine düşer ve altındaki API çağrıları eskisi gibi 403 ile hata gösterir - yeni bir
 * yetki kazandırılmaz.
 */
export default function SettingsPage() {
  const [role, setRole] = useState<StaffContext["role"] | null>(null);
  const [activeTab, setActiveTab] = useState<TabId>("isletme");

  useEffect(() => {
    let cancelled = false;
    me()
      .then((context) => {
        if (cancelled) return;
        setRole(context.role);
      })
      .catch(() => {
        if (!cancelled) setRole("CASHIER");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <AppShell>
      <main className={`${styles.page} ${pageStyles.page}`}>
        <PageHeader title="Ayarlar" description="İşletme ve şube ayarlarını yönetin." />

        {role === null ? (
          <TableSkeleton />
        ) : role === "BUSINESS_ADMIN" ? (
          <>
            <Tabs items={TAB_ITEMS} activeId={activeTab} onChange={(id) => setActiveTab(id as TabId)} ariaLabel="Ayarlar" />
            {activeTab === "isletme" ? (
              <div className={pageStyles.tabPanel}>
                <p className={pageStyles.tabHint}>
                  Şube ile ilgili ayarlar ve şube bazlı yapılandırmalar “Şube” sekmesinde yönetilir.
                </p>
                <BusinessTab />
              </div>
            ) : (
              <div className={pageStyles.tabPanel}>
                <BranchTab role={role} />
              </div>
            )}
          </>
        ) : (
          <div className={pageStyles.tabPanel}>
            <BranchTab role={role} />
          </div>
        )}
      </main>
    </AppShell>
  );
}
