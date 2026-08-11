"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { login } from "@/lib/api";
import Button from "@/components/ui/Button";
import styles from "./page.module.css";

/**
 * Real StaffUser login (Section 4, staff-web screen #1 - Milestone 8 replaces the
 * Milestone 6 "branch id + shared token" kiosk entry screen). On success the backend
 * sets the HttpOnly qrmenu_staff_session cookie itself; there is nothing for the
 * client to store. Every role lands on /branches - BUSINESS_ADMIN/PLATFORM_ADMIN see
 * the full admin nav there, BRANCH_MANAGER/KITCHEN_STAFF only what their permissions
 * allow (enforced server-side regardless of what the nav shows).
 */
export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      router.push("/branches");
    } catch {
      setError("E-posta veya şifre hatalı.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className={styles.page}>
      <form className={styles.card} onSubmit={handleSubmit}>
        <h1 className={styles.title}>Personel Girişi</h1>
        <p className={styles.subtitle}>QR Menü yönetim ve mutfak ekranlarına erişmek için giriş yapın.</p>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="email">
            E-posta
          </label>
          <input
            id="email"
            type="email"
            className={styles.input}
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="password">
            Şifre
          </label>
          <input
            id="password"
            type="password"
            className={styles.input}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </div>

        {error ? <p className={styles.error}>{error}</p> : null}

        <Button type="submit" size="lg" className={styles.submit} disabled={submitting}>
          {submitting ? "Giriş yapılıyor…" : "Giriş Yap"}
        </Button>
      </form>
    </main>
  );
}
