import {
  BarChart3,
  BookOpen,
  ClipboardList,
  History,
  LayoutDashboard,
  Scale,
  Settings,
  ShoppingBag,
  Store,
  Table2,
  Undo2,
  User,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import type { StaffContext } from "./api";

export const ROLE_LABELS: Record<string, string> = {
  PLATFORM_ADMIN: "Platform Yöneticisi",
  BUSINESS_ADMIN: "İşletme Yöneticisi",
  BRANCH_MANAGER: "Şube Sorumlusu",
  CASHIER: "Kasa",
};

export type StaffRole = StaffContext["role"];

export function reportsHref(context: StaffContext): string | null {
  return context.activeBranchId ? "/reports" : null;
}

export type NavItem = {
  key: string;
  label: string;
  matchPrefix: string;
  roles: StaffRole[];
  href: (context: StaffContext) => string | null;
  icon: LucideIcon;
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
        label: "Özet",
        matchPrefix: "/dashboard",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN", "BRANCH_MANAGER", "CASHIER"],
        href: () => "/dashboard",
        icon: LayoutDashboard,
      },
      {
        key: "cashier",
        label: "Kasa",
        matchPrefix: "/cashier",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN", "BRANCH_MANAGER", "CASHIER"],
        href: (context) => (context.activeBranchId ? "/cashier" : null),
        icon: ShoppingBag,
      },
      {
        key: "orders",
        label: "Siparişler",
        matchPrefix: "/orders",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN", "BRANCH_MANAGER", "CASHIER"],
        href: (context) => (context.activeBranchId ? "/orders" : null),
        icon: ClipboardList,
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
        icon: BookOpen,
      },
      {
        key: "tables",
        label: "Masalar",
        matchPrefix: "/tables",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN"],
        href: () => "/tables",
        icon: Table2,
      },
      {
        key: "branch-settings",
        label: "Şube Ayarları",
        matchPrefix: "/branches",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN"],
        href: (context) => (context.activeBranchId ? "/branches" : null),
        icon: Store,
      },
      {
        key: "staff",
        label: "Personel",
        matchPrefix: "/staff",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN"],
        href: () => "/staff",
        icon: User,
      },
    ],
  },
  {
    title: "Finans",
    items: [
      {
        key: "reports",
        label: "Raporlar",
        matchPrefix: "/reports",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN", "BRANCH_MANAGER", "CASHIER"],
        href: reportsHref,
        icon: BarChart3,
      },
      {
        key: "chain-reports",
        label: "Zincir Raporları",
        matchPrefix: "/chain-reports",
        roles: ["PLATFORM_ADMIN"],
        href: () => "/chain-reports",
        icon: BarChart3,
      },
      {
        key: "chain-comparison",
        label: "Şube Karşılaştırma",
        matchPrefix: "/chain-comparison",
        roles: ["PLATFORM_ADMIN"],
        href: () => "/chain-comparison",
        icon: Scale,
      },
      {
        key: "expenses",
        label: "Giderler",
        matchPrefix: "/expenses",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN", "BRANCH_MANAGER"],
        href: () => "/expenses",
        icon: Wallet,
      },
      {
        key: "refunds",
        label: "İadeler",
        matchPrefix: "/refunds",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN", "BRANCH_MANAGER"],
        href: (context) => (context.activeBranchId ? "/refunds" : null),
        icon: Undo2,
      },
    ],
  },
  {
    title: "Sistem",
    items: [
      {
        key: "audit",
        label: "Denetim Kaydı",
        matchPrefix: "/audit",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN"],
        href: () => "/audit",
        icon: History,
      },
      {
        key: "business-settings",
        label: "İşletme Ayarları",
        matchPrefix: "/business-settings",
        roles: ["PLATFORM_ADMIN", "BUSINESS_ADMIN"],
        href: () => "/business-settings",
        icon: Settings,
      },
    ],
  },
];
