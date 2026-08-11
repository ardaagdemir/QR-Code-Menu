"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { logout, me, type StaffContext } from "@/lib/api";
import AnnouncementBanner from "./AnnouncementBanner";
import styles from "./StaffNav.module.css";

const ROLE_LABELS: Record<string, string> = {
  PLATFORM_ADMIN: "Platform Yöneticisi",
  BUSINESS_ADMIN: "İşletme Yöneticisi",
  BRANCH_MANAGER: "Şube Sorumlusu",
  CASHIER: "Kasa",
  KITCHEN_STAFF: "Mutfak Personeli",
};

/**
 * Shown at the top of every session-gated staff-web screen. Fetches /me once to
 * confirm the session is still valid (redirecting to the login page on 401 - the
 * same guard every page used to do individually against localStorage) and to decide
 * which admin links to show: Branch/Menu/Staff/Audit management are BUSINESS_ADMIN-
 * and PLATFORM_ADMIN-only permissions (Section 5's role table), so BRANCH_MANAGER/
 * KITCHEN_STAFF simply don't see those links - the backend still enforces this
 * either way, this is purely a "don't show a link that will 403" nicety.
 */
export default function StaffNav() {
  const [context, setContext] = useState<StaffContext | null>(null);
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    let cancelled = false;
    me()
      .then((ctx) => {
        if (!cancelled) {
          setContext(ctx);
        }
      })
      .catch(() => {
        if (!cancelled) {
          router.replace("/");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [router]);

  async function handleLogout() {
    await logout().catch(() => undefined);
    router.replace("/");
  }

  const isAdmin = context?.role === "BUSINESS_ADMIN" || context?.role === "PLATFORM_ADMIN";

  function linkClass(href: string) {
    return pathname?.startsWith(href) ? `${styles.link} ${styles.active}` : styles.link;
  }

  return (
    <>
      <nav className={styles.nav}>
        <div className={styles.links}>
          {isAdmin ? (
            <>
              <Link href="/branches" className={linkClass("/branches")}>
                Şubeler
              </Link>
              <Link href="/menu" className={linkClass("/menu")}>
                Menü
              </Link>
              <Link href="/staff" className={linkClass("/staff")}>
                Personel
              </Link>
              <Link href="/audit" className={linkClass("/audit")}>
                Denetim Kaydı
              </Link>
              <Link href="/business-settings" className={linkClass("/business-settings")}>
                İşletme Ayarları
              </Link>
              <Link href="/announcements" className={linkClass("/announcements")}>
                Duyurular
              </Link>
              <Link href="/chain-comparison" className={linkClass("/chain-comparison")}>
                Şube Karşılaştırma
              </Link>
              <Link href="/reports" className={linkClass("/reports")}>
                Satış Raporları
              </Link>
              <Link href="/expenses" className={linkClass("/expenses")}>
                Giderler
              </Link>
            </>
          ) : null}
          {context?.role === "BRANCH_MANAGER" ? (
            <Link href="/expenses" className={linkClass("/expenses")}>
              Giderler
            </Link>
          ) : null}
        </div>
        <div className={styles.right}>
          {context ? (
            <span className={styles.identity}>
              {context.email}
              <br />
              {ROLE_LABELS[context.role] ?? context.role}
            </span>
          ) : null}
          <button type="button" className={styles.logout} onClick={handleLogout}>
            Çıkış Yap
          </button>
        </div>
      </nav>
      {context ? <AnnouncementBanner /> : null}
    </>
  );
}
