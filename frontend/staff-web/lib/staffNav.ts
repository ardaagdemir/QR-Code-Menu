import type { StaffContext } from "./api";

export const ROLE_LABELS: Record<string, string> = {
  PLATFORM_ADMIN: "Platform Yöneticisi",
  BUSINESS_ADMIN: "İşletme Yöneticisi",
  BRANCH_MANAGER: "Şube Sorumlusu",
  CASHIER: "Kasa",
  KITCHEN_STAFF: "Mutfak Personeli",
};

export type StaffRole = StaffContext["role"];

/** True for roles whose backend permissions span every branch in the business
 * (Permission.BRANCH_MANAGE), so they pick a branch via /branches rather than being
 * deep-linked to one. */
export function isBusinessWide(role: StaffRole): boolean {
  return role === "BUSINESS_ADMIN" || role === "PLATFORM_ADMIN";
}

/** Kasa/Mutfak/İadeler have no "all branches" screen - a business-wide role goes
 * through /branches to pick one, an operator scoped to their own branch(es) goes
 * straight there (first assigned branch, the common single-branch case). */
export function singleBranchHref(context: StaffContext, basePath: string): string | null {
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
export function reportsHref(context: StaffContext): string | null {
  if (isBusinessWide(context.role)) {
    return "/reports";
  }
  if (context.branches.length === 0) {
    return null;
  }
  return `/reports/${context.branches[0].id}`;
}

export type NavItem = {
  key: string;
  label: string;
  matchPrefix: string;
  roles: StaffRole[];
  href: (context: StaffContext) => string | null;
};

export type NavGroup = {
  title: string;
  items: NavItem[];
};

/** Bölüm 19.3'ün önerdiği bilgi mimarisi. Her linkin görünürlüğü StaffRole.permissions()
 * (backend, StaffRole.java) ile eşleşir - bu yalnızca 403'e gidecek bir linki
 * gizleme niceliğidir, gerçek yetkilendirme backend'de kalır. */
export const NAV_GROUPS: NavGroup[] = [
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
