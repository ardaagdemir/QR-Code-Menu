"use client";

import { useEffect, useId, useState } from "react";
import type { ReactNode } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { CircleHelp, Eye, EyeOff, KeyRound, LogOut } from "lucide-react";
import { ApiError, changePassword, logout, me, MIN_PASSWORD_LENGTH, type StaffContext } from "@/lib/api";
import { NAV_GROUPS, ROLE_LABELS } from "@/lib/staffNav";
import IconButton from "@/components/ui/IconButton";
import Dialog from "@/components/ui/Dialog";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Button from "@/components/ui/Button";
import ErrorState from "@/components/ui/ErrorState";
import { useToast } from "@/components/ui/ToastProvider";
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
  const { showToast } = useToast();
  const changePasswordDialogTitleId = useId();

  const [changePasswordOpen, setChangePasswordOpen] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmNewPassword, setConfirmNewPassword] = useState("");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);
  const [changePasswordError, setChangePasswordError] = useState<string | null>(null);

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

  function openChangePasswordDialog() {
    setChangePasswordError(null);
    setCurrentPassword("");
    setNewPassword("");
    setConfirmNewPassword("");
    setPasswordVisible(false);
    setChangePasswordOpen(true);
  }

  async function handleChangePassword(event: React.FormEvent) {
    event.preventDefault();
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setChangePasswordError(`Yeni şifre en az ${MIN_PASSWORD_LENGTH} karakter olsun.`);
      return;
    }
    if (newPassword !== confirmNewPassword) {
      setChangePasswordError("Yeni şifreler eşleşmiyor.");
      return;
    }
    setChangingPassword(true);
    setChangePasswordError(null);
    try {
      await changePassword(currentPassword, newPassword, confirmNewPassword);
      setChangePasswordOpen(false);
      showToast("Şifreniz değiştirildi.", "success");
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setChangePasswordError("Mevcut şifre yanlış.");
      } else {
        setChangePasswordError("Şifre değiştirilemedi.");
      }
    } finally {
      setChangingPassword(false);
    }
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
            <IconButton aria-label="Şifremi Değiştir" size="sm" onClick={openChangePasswordDialog}>
              <KeyRound size={15} />
            </IconButton>
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
        <div className={styles.content}>{children}</div>
      </div>

      {changePasswordOpen ? (
        <Dialog onClose={() => setChangePasswordOpen(false)} labelledBy={changePasswordDialogTitleId}>
          <h2 id={changePasswordDialogTitleId} className={styles.dialogTitle}>
            Şifremi Değiştir
          </h2>
          <form className={styles.dialogForm} onSubmit={handleChangePassword}>
            <FormField label="Mevcut Şifre" required>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  type="password"
                  value={currentPassword}
                  onChange={(event) => setCurrentPassword(event.target.value)}
                  autoComplete="current-password"
                  required
                />
              )}
            </FormField>
            <FormField label="Yeni Şifre" hint={`En az ${MIN_PASSWORD_LENGTH} karakter`} required>
              {(controlProps) => (
                <div className={styles.passwordField}>
                  <Input
                    {...controlProps}
                    type={passwordVisible ? "text" : "password"}
                    className={styles.passwordInput}
                    value={newPassword}
                    onChange={(event) => setNewPassword(event.target.value)}
                    autoComplete="new-password"
                    required
                  />
                  <button
                    type="button"
                    className={styles.passwordToggle}
                    onClick={() => setPasswordVisible((visible) => !visible)}
                    aria-label={passwordVisible ? "Şifreyi gizle" : "Şifreyi göster"}
                    aria-pressed={passwordVisible}
                    disabled={changingPassword}
                  >
                    {passwordVisible ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
                  </button>
                </div>
              )}
            </FormField>
            <FormField label="Yeni Şifre (Tekrar)" required>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  type={passwordVisible ? "text" : "password"}
                  value={confirmNewPassword}
                  onChange={(event) => setConfirmNewPassword(event.target.value)}
                  autoComplete="new-password"
                  required
                />
              )}
            </FormField>

            {changePasswordError ? <ErrorState message={changePasswordError} /> : null}

            <Button type="submit" disabled={changingPassword}>
              {changingPassword ? "Değiştiriliyor…" : "Şifreyi Değiştir"}
            </Button>
          </form>
        </Dialog>
      ) : null}
    </div>
  );
}
