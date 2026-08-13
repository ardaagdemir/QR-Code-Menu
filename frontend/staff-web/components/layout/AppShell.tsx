"use client";

import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { logout, me, type StaffContext } from "@/lib/api";
import { NAV_GROUPS, ROLE_LABELS } from "@/lib/staffNav";
import IconButton from "@/components/ui/IconButton";
import AnnouncementBanner from "./AnnouncementBanner";
import styles from "./AppShell.module.css";

type Props = {
  children: ReactNode;
  /**
   * "warm": scopes the "Kasa" screen's own identity (Bölüm 19.1 dışı, ürün kararı)
   * onto this AppShell instance only via a CSS custom-property override (see
   * AppShell.module.css `.warm`) - every other route renders its own AppShell without
   * this prop, so the shared "Tide" chrome elsewhere is untouched.
   */
  theme?: "default" | "warm";
};

/**
 * Shown around every session-gated staff-web screen (replaces the old top-link
 * StaffNav). Fetches /me once to confirm the session is still valid (redirecting to
 * the login page on 401) and to decide which nav links to show. Desktop (>=1024px):
 * persistent left sidebar + top bar. Smaller screens: hamburger-triggered drawer
 * (Bölüm 19.3/19.4).
 */
export default function AppShell({ children, theme = "default" }: Props) {
  const [context, setContext] = useState<StaffContext | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
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

  function isActive(matchPrefix: string) {
    return pathname?.startsWith(matchPrefix) ?? false;
  }

  const navContent = (
    <>
      <div className={styles.brand}>QR Menü</div>
      <nav className={styles.nav}>
        {NAV_GROUPS.map((group) => {
          const visibleItems = context
            ? group.items.filter((item) => item.roles.includes(context.role) && item.href(context) !== null)
            : [];
          if (visibleItems.length === 0) {
            return null;
          }
          return (
            <div key={group.title} className={styles.group}>
              <div className={styles.groupTitle}>{group.title}</div>
              {visibleItems.map((item) => (
                <Link
                  key={item.key}
                  href={item.href(context as StaffContext) as string}
                  className={isActive(item.matchPrefix) ? `${styles.link} ${styles.active}` : styles.link}
                  onClick={() => setDrawerOpen(false)}
                >
                  {item.label}
                </Link>
              ))}
            </div>
          );
        })}
      </nav>
    </>
  );

  return (
    <div className={theme === "warm" ? `${styles.shell} ${styles.warm}` : styles.shell}>
      {drawerOpen ? <div className={styles.backdrop} onClick={() => setDrawerOpen(false)} /> : null}
      <aside className={drawerOpen ? `${styles.sidebar} ${styles.sidebarOpen}` : styles.sidebar}>{navContent}</aside>
      <div className={styles.main}>
        <header className={styles.topbar}>
          <div className={styles.topbarLeft}>
            <IconButton
              aria-label="Menüyü aç"
              className={styles.hamburger}
              onClick={() => setDrawerOpen((open) => !open)}
            >
              ☰
            </IconButton>
            {context ? (
              <span className={styles.context}>
                <span className={styles.contextBusiness}>{context.businessName}</span>
                {context.branches.length > 0 ? ` · ${context.branches.map((branch) => branch.name).join(", ")}` : ""}
              </span>
            ) : null}
          </div>
          <div className={styles.topbarRight}>
            {context ? (
              <span className={styles.identity}>
                {context.email} · {ROLE_LABELS[context.role] ?? context.role}
              </span>
            ) : null}
            <button type="button" className={styles.logout} onClick={handleLogout}>
              Çıkış Yap
            </button>
          </div>
        </header>
        {context ? <AnnouncementBanner /> : null}
        <div className={styles.content}>{children}</div>
      </div>
    </div>
  );
}
