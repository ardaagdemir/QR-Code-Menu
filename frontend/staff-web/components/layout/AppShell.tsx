"use client";

import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { CircleHelp, LogOut } from "lucide-react";
import { logout, me, type StaffContext } from "@/lib/api";
import { NAV_GROUPS, ROLE_LABELS } from "@/lib/staffNav";
import IconButton from "@/components/ui/IconButton";
import AnnouncementBanner from "./AnnouncementBanner";
import ThemeToggle from "./ThemeToggle";
import styles from "./AppShell.module.css";

function initialsFromEmail(email: string): string {
  const local = email.split("@")[0] ?? email;
  const parts = local.split(/[._-]/).filter(Boolean);
  const letters = parts.length > 1 ? parts[0][0] + parts[1][0] : local.slice(0, 2);
  return letters.toUpperCase();
}

type Props = {
  children: ReactNode;
};

/**
 * Shown around every session-gated staff-web screen (replaces the old top-link
 * StaffNav). Fetches /me once to confirm the session is still valid (redirecting to
 * the login page on 401) and to decide which nav links to show. Desktop (>=1024px):
 * persistent left sidebar + top bar. Smaller screens: hamburger-triggered drawer
 * (Bölüm 19.3/19.4). Every screen (Kasa included) shares one visual identity - see
 * development-progress.md "staff-web Görsel Yön Değişikliği".
 */
export default function AppShell({ children }: Props) {
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
      <div className={styles.brand}>
        <span className={styles.brandMark} aria-hidden="true">
          Q
        </span>
        <span className={styles.brandWordmark}>QR Menü</span>
      </div>
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
              {visibleItems.map((item) => {
                const Icon = item.icon;
                return (
                  <Link
                    key={item.key}
                    href={item.href(context as StaffContext) as string}
                    className={isActive(item.matchPrefix) ? `${styles.link} ${styles.active}` : styles.link}
                    onClick={() => setDrawerOpen(false)}
                  >
                    <Icon size={17} className={styles.linkIcon} aria-hidden="true" />
                    {item.label}
                  </Link>
                );
              })}
            </div>
          );
        })}
      </nav>
      {context ? (
        <div className={styles.sidebarFooter}>
          <div className={styles.userCard}>
            <span className={styles.avatar} aria-hidden="true">
              {initialsFromEmail(context.email)}
            </span>
            <span className={styles.userMeta}>
              <span className={styles.userEmail}>{context.email}</span>
              <span className={styles.userRole}>{ROLE_LABELS[context.role] ?? context.role}</span>
            </span>
            <IconButton aria-label="Çıkış Yap" size="sm" onClick={handleLogout}>
              <LogOut size={15} />
            </IconButton>
          </div>
          <div className={styles.supportCard}>
            <CircleHelp size={17} aria-hidden="true" />
            <span>Yardım &amp; Destek</span>
          </div>
        </div>
      ) : null}
    </>
  );

  return (
    <div className={styles.shell}>
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
                {context.activeBranchName ? ` · ${context.activeBranchName}` : ""}
              </span>
            ) : null}
          </div>
          <div className={styles.topbarRight}>
            <ThemeToggle />
          </div>
        </header>
        {context ? <AnnouncementBanner /> : null}
        <div className={styles.content}>{children}</div>
      </div>
    </div>
  );
}
