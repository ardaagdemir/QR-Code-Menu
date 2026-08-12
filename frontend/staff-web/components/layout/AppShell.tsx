"use client";

import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { logout, me, type StaffContext } from "@/lib/api";
import IconButton from "@/components/ui/IconButton";
import AnnouncementBanner from "./AnnouncementBanner";
import styles from "./AppShell.module.css";

const ROLE_LABELS: Record<string, string> = {
  PLATFORM_ADMIN: "Platform Yöneticisi",
  BUSINESS_ADMIN: "İşletme Yöneticisi",
  BRANCH_MANAGER: "Şube Sorumlusu",
  CASHIER: "Kasa",
  KITCHEN_STAFF: "Mutfak Personeli",
};

type StaffRole = StaffContext["role"];

/** True for roles whose backend permissions span every branch in the business
 * (Permission.BRANCH_MANAGE), so they pick a branch via /branches rather than being
 * deep-linked to one. */
function isBusinessWide(role: StaffRole): boolean {
  return role === "BUSINESS_ADMIN" || role === "PLATFORM_ADMIN";
}

/** Kasa/Mutfak/İadeler have no "all branches" screen - a business-wide role goes
 * through /branches to pick one, an operator scoped to their own branch(es) goes
 * straight there (first assigned branch, the common single-branch case). */
function singleBranchHref(context: StaffContext, basePath: string): string | null {
  if (isBusinessWide(context.role)) {
    return "/branches";
  }
  if (context.branches.length === 0) {
    return null;
  }
  return `${basePath}/${context.branches[0].id}`;
}

/** Unlike Kasa/Mutfak/İadeler, sales reporting has a real "all branches" screen
 * (Permission.REPORT_CHAIN_VIEW, /reports) - business-wide roles land there directly
 * instead of being routed through /branches first. */
function reportsHref(context: StaffContext): string | null {
  if (isBusinessWide(context.role)) {
    return "/reports";
  }
  if (context.branches.length === 0) {
    return null;
  }
  return `/reports/${context.branches[0].id}`;
}

type NavItem = {
  key: string;
  label: string;
  matchPrefix: string;
  roles: StaffRole[];
  href: (context: StaffContext) => string | null;
};

type NavGroup = {
  title: string;
  items: NavItem[];
};

/** Bölüm 19.3'ün önerdiği bilgi mimarisi. Her linkin görünürlüğü StaffRole.permissions()
 * (backend, StaffRole.java) ile eşleşir - bu yalnızca 403'e gidecek bir linki
 * gizleme niceliğidir, gerçek yetkilendirme backend'de kalır. */
const NAV_GROUPS: NavGroup[] = [
  {
    title: "Operasyon",
    items: [
      {
        key: "dashboard",
        label: "Dashboard",
        matchPrefix: "/dashboard",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN", "BRANCH_MANAGER", "CASHIER", "KITCHEN_STAFF"],
        href: () => "/dashboard",
      },
      {
        key: "cashier",
        label: "Kasa",
        matchPrefix: "/cashier",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN", "BRANCH_MANAGER", "CASHIER"],
        href: (context) => singleBranchHref(context, "/cashier"),
      },
      {
        key: "kitchen",
        label: "Mutfak",
        matchPrefix: "/kitchen",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN", "BRANCH_MANAGER", "KITCHEN_STAFF"],
        href: (context) => singleBranchHref(context, "/kitchen"),
      },
    ],
  },
  {
    title: "Yönetim",
    items: [
      {
        key: "menu",
        label: "Menü",
        matchPrefix: "/menu",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN"],
        href: () => "/menu",
      },
      {
        key: "branches",
        label: "Şubeler / Masalar / QR",
        matchPrefix: "/branches",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN"],
        href: () => "/branches",
      },
      {
        key: "staff",
        label: "Personel",
        matchPrefix: "/staff",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN"],
        href: () => "/staff",
      },
    ],
  },
  {
    title: "Finans",
    items: [
      {
        key: "reports",
        label: "Satış Raporları",
        matchPrefix: "/reports",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN", "BRANCH_MANAGER", "CASHIER"],
        href: reportsHref,
      },
      {
        key: "chain-comparison",
        label: "Şube Karşılaştırma",
        matchPrefix: "/chain-comparison",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN"],
        href: () => "/chain-comparison",
      },
      {
        key: "expenses",
        label: "Giderler",
        matchPrefix: "/expenses",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN", "BRANCH_MANAGER"],
        href: () => "/expenses",
      },
      {
        key: "refunds",
        label: "İadeler",
        matchPrefix: "/refunds",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN", "BRANCH_MANAGER"],
        href: (context) => singleBranchHref(context, "/refunds"),
      },
    ],
  },
  {
    title: "Sistem",
    items: [
      {
        key: "announcements",
        label: "Duyurular",
        matchPrefix: "/announcements",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN"],
        href: () => "/announcements",
      },
      {
        key: "audit",
        label: "Denetim Kaydı",
        matchPrefix: "/audit",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN"],
        href: () => "/audit",
      },
      {
        key: "business-settings",
        label: "İşletme Ayarları",
        matchPrefix: "/business-settings",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN"],
        href: () => "/business-settings",
      },
    ],
  },
];

type Props = {
  children: ReactNode;
};

/**
 * Shown around every session-gated staff-web screen (replaces the old top-link
 * StaffNav). Fetches /me once to confirm the session is still valid (redirecting to
 * the login page on 401) and to decide which nav links to show. Desktop (>=1024px):
 * persistent left sidebar + top bar. Smaller screens: hamburger-triggered drawer
 * (Bölüm 19.3/19.4).
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
                {context.businessName}
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
