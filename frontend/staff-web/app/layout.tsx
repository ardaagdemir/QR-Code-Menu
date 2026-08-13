import type { Metadata, Viewport } from "next";
import { Bricolage_Grotesque, Inter, Plus_Jakarta_Sans } from "next/font/google";
import { ToastProvider } from "@/components/ui/ToastProvider";
import { THEME_INIT_SCRIPT } from "@/lib/theme";
import "./globals.css";

// next/font build zamanında self-host eder (runtime CDN isteği yok);
// latin-ext alt kümesi Türkçe karakterleri (ç ğ ı ö ş ü) kapsar.
const bodyFont = Inter({
  subsets: ["latin", "latin-ext"],
  variable: "--font-body",
  display: "swap",
});

const displayFont = Plus_Jakarta_Sans({
  subsets: ["latin", "latin-ext"],
  weight: ["600", "700", "800"],
  variable: "--font-display",
  display: "swap",
});

// Kasa'nın sıcak kimliği için ayrı bir display fontu (ürün kararı) - kendi CSS
// değişkeninde yaşar ve yalnızca AppShell'in `.warm` scope'unda (bkz.
// AppShell.module.css) --font-family-display bunu işaret eder, diğer ekranların
// display fontu (Plus Jakarta Sans) bu eklemeden etkilenmez.
const displayFontWarm = Bricolage_Grotesque({
  subsets: ["latin", "latin-ext"],
  weight: ["600", "700", "800"],
  variable: "--font-display-warm",
  display: "swap",
});

export const metadata: Metadata = {
  title: "QR Menü - Personel Paneli",
  description: "QR Menü platformu personel/yönetim ekranı",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="tr" className={`${bodyFont.variable} ${displayFont.variable} ${displayFontWarm.variable}`}>
      <body>
        {/* Must run before hydration/paint, as the very first thing in <body>, so a
            returning user who chose dark doesn't see a light flash first (see
            lib/theme.ts - it never reads prefers-color-scheme, only the stored choice). */}
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
        <ToastProvider>{children}</ToastProvider>
      </body>
    </html>
  );
}
