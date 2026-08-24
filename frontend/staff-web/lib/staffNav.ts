import {
  BarChart3,
  BookOpen,
  Building2,
  ClipboardList,
  History,
  LayoutDashboard,
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

/**
 * Bölüm 19.3'ün önerdiği bilgi mimarisi. Her linkin görünürlüğü StaffRole.permissions()
 * (backend, StaffRole.java) ile eşleşir - bu yalnızca 403'e gidecek bir linki
 * gizleme niceliğidir, gerçek yetkilendirme backend'de kalır.
 *
 * PLATFORM_ADMIN scope narrowing: PLATFORM_ADMIN artık StaffRole.permissions()'ta hiçbir
 * normal-staff Permission'a sahip değil (yalnızca /api/platform-admin/** rol bazlı çalışır),
 * bu yüzden aşağıdaki hiçbir normal işletme operasyonu item'ında PLATFORM_ADMIN yok - sadece
 * "Platform" grubundaki "İşletmeler" linki. PLATFORM_ADMIN girişinde kendi hesap/şifre/çıkış
 * alanları (AppShell'in sidebar footer'ı) zaten role bakılmaksızın her zaman gösteriliyor.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    title: "Platform",
    items: [
      {
        key: "platform-admin-businesses",
        label: "İşletmeler",
        matchPrefix: "/platform-admin",
        roles: ["PLATFORM_ADMIN"],
        href: () => "/platform-admin/businesses",
        icon: Building2,
      },
    ],
  },
  {
    title: "Operasyon",
    items: [
      {
        key: "dashboard",
        label: "Özet",
        matchPrefix: "/dashboard",
        roles: ["BUSINESS_ADMIN", "BRANCH_MANAGER", "CASHIER"],
        href: () => "/dashboard",
        icon: LayoutDashboard,
      },
      {
        key: "cashier",
        label: "Kasa",
        matchPrefix: "/cashier",
        roles: ["BUSINESS_ADMIN", "BRANCH_MANAGER", "CASHIER"],
        href: (context) => (context.activeBranchId ? "/cashier" : null),
        icon: ShoppingBag,
      },
      {
        key: "orders",
        label: "Siparişler",
        matchPrefix: "/orders",
        roles: ["BUSINESS_ADMIN", "BRANCH_MANAGER", "CASHIER"],
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
        roles: ["BUSINESS_ADMIN"],
        href: () => "/menu",
        icon: BookOpen,
      },
      {
        key: "tables",
        label: "Masalar",
        matchPrefix: "/tables",
        roles: ["BUSINESS_ADMIN"],
        href: () => "/tables",
        icon: Table2,
      },
      {
        key: "branch-settings",
        label: "Şube Ayarları",
        matchPrefix: "/branches",
        roles: ["BUSINESS_ADMIN", "BRANCH_MANAGER"],
        href: (context) => (context.activeBranchId ? "/branches" : null),
        icon: Store,
      },
      {
        key: "staff",
        label: "Personel",
        matchPrefix: "/staff",
        roles: ["BUSINESS_ADMIN"],
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
        roles: ["BUSINESS_ADMIN", "BRANCH_MANAGER", "CASHIER"],
        href: reportsHref,
        icon: BarChart3,
      },
      {
        key: "expenses",
        label: "Giderler",
        matchPrefix: "/expenses",
        roles: ["BUSINESS_ADMIN", "BRANCH_MANAGER"],
        href: () => "/expenses",
        icon: Wallet,
      },
      {
        key: "refunds",
        label: "İadeler",
        matchPrefix: "/refunds",
        roles: ["BUSINESS_ADMIN", "BRANCH_MANAGER"],
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
        roles: ["BUSINESS_ADMIN"],
        href: () => "/audit",
        icon: History,
      },
      {
        key: "business-settings",
        label: "İşletme Ayarları",
        matchPrefix: "/business-settings",
        roles: ["BUSINESS_ADMIN"],
        href: () => "/business-settings",
        icon: Settings,
      },
    ],
  },
];
