import type { Metadata, Viewport } from "next";
import { ToastProvider } from "@/components/ui/ToastProvider";
import "./globals.css";

export const metadata: Metadata = {
  title: "QR Menü",
  description: "QR kod ile menü görüntüleme ve sipariş platformu",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="tr">
      <body>
        <ToastProvider>{children}</ToastProvider>
      </body>
    </html>
  );
}
