"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, CircleAlert, Eye, EyeOff, LoaderCircle, Lock, Mail } from "lucide-react";
import { login } from "@/lib/api";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import styles from "./page.module.css";

/**
 * Real StaffUser login (Section 4, staff-web screen #1 - Milestone 8 replaces the
 * Milestone 6 "branch id + shared token" kiosk entry screen). On success the backend
 * sets the HttpOnly qrmenu_staff_session cookie itself; there is nothing for the
 * client to store. Every normal-staff role with an active branch lands on /cashier
 * (a branchless BUSINESS_ADMIN falls back to /menu) - AppShell's sidebar shows only
 * what the role's permissions allow (enforced server-side regardless of what the nav
 * shows). PLATFORM_ADMIN holds no normal-staff Permission at all (StaffRole.java) and
 * would just hit a wall of 403s on those routes, so it lands on the platform admin
 * panel instead.
 */
export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const context = await login(email.trim(), password);
      router.push(
        context.role === "PLATFORM_ADMIN"
          ? "/platform-admin/businesses"
          : context.activeBranchId
            ? "/cashier"
            : "/menu",
      );
    } catch {
      setError("E-posta veya şifre hatalı.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className={styles.page}>
      <section className={styles.card} aria-labelledby="login-title">
        <div className={styles.brand} aria-label="QR Menü">
          <span className={styles.brandMark} aria-hidden="true">
            Q
          </span>
          <span className={styles.brandWordmark}>QR Menü</span>
        </div>

        <div className={styles.heading}>
          <h1 id="login-title" className={styles.title}>
            Hesabınıza giriş yapın
          </h1>
          <p className={styles.subtitle}>QR Menü yönetim paneline devam etmek için bilgilerinizi girin.</p>
        </div>

        <form className={styles.form} onSubmit={handleSubmit} aria-busy={submitting}>
          <div className={styles.field}>
            <label className={styles.label} htmlFor="email">
              E-posta
            </label>
            <Input
              id="email"
              type="email"
              icon={<Mail size={18} aria-hidden="true" />}
              placeholder="ornek@eposta.com"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              disabled={submitting}
              required
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label} htmlFor="password">
              Şifre
            </label>
            <div className={styles.passwordField}>
              <Input
                id="password"
                type={passwordVisible ? "text" : "password"}
                icon={<Lock size={18} aria-hidden="true" />}
                placeholder="Şifrenizi girin"
                className={styles.passwordInput}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
                disabled={submitting}
                required
              />
              <button
                type="button"
                className={styles.passwordToggle}
                onClick={() => setPasswordVisible((visible) => !visible)}
                aria-label={passwordVisible ? "Şifreyi gizle" : "Şifreyi göster"}
                aria-pressed={passwordVisible}
                disabled={submitting}
              >
                {passwordVisible ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
              </button>
            </div>
          </div>

          {error ? (
            <div className={styles.error} role="alert">
              <CircleAlert aria-hidden="true" />
              <span>{error}</span>
            </div>
          ) : null}

          <Button type="submit" size="lg" className={styles.submit} disabled={submitting}>
            {submitting ? (
              <>
                <LoaderCircle className={styles.spinner} aria-hidden="true" />
                Giriş yapılıyor…
              </>
            ) : (
              <>
                Giriş Yap
                <ArrowRight className={styles.submitIcon} aria-hidden="true" />
              </>
            )}
          </Button>
        </form>
      </section>
    </main>
  );
}
