"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ApiError, formatPriceMinorUnits, getBranchSalesReport, type BranchSalesReport } from "@/lib/api";
import StaffNav from "@/components/layout/StaffNav";
import Button from "@/components/ui/Button";
import adminStyles from "@/styles/admin.module.css";
import styles from "../reports.module.css";

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Gap-analysis #8 (product-requirements.md Section 13.1): tek şube için seçilebilir
 * tarih aralığında brüt/net satış, refund, sipariş/kabul/red sayısı, ortalama sepet,
 * ürün/kategori kırılımı, saatlik dağılım. CASHIER/BRANCH_MANAGER/BUSINESS_ADMIN
 * (Permission.REPORT_VIEW) kendi şubeleri için görebilir - backend branch-scope'u
 * zaten zorunlu kılıyor, bu sayfa doğrudan URL ile de (Kasa/Mutfak ekranlarıyla aynı
 * gezinme deseni) erişilebilir.
 */
export default function BranchReportPage() {
  const params = useParams<{ branchId: string }>();
  const branchId = params.branchId;

  const [from, setFrom] = useState(todayIsoDate());
  const [to, setTo] = useState(todayIsoDate());
  const [report, setReport] = useState<BranchSalesReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  /** setState calls only happen inside the promise callbacks, never synchronously - safe to call from an effect body. */
  function applyReport(promise: Promise<BranchSalesReport>) {
    promise
      .then((data) => {
        setReport(data);
        setError(null);
      })
      .catch((err) => setError(err instanceof ApiError ? "Rapor yüklenemedi." : "Beklenmedik bir hata oluştu."))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    applyReport(getBranchSalesReport(branchId, from, to));
    // Only re-fetch automatically when the branch changes - date range changes are applied via the "Uygula" button.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId]);

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setLoading(true);
    applyReport(getBranchSalesReport(branchId, from, to));
  }

  return (
    <>
      <StaffNav />
      <main className={adminStyles.page}>
        <div className={adminStyles.header}>
          <h1 className={adminStyles.title}>Şube Raporu</h1>
        </div>

        <form className={styles.filters} onSubmit={handleSubmit}>
          <div className={adminStyles.field}>
            <label className={adminStyles.label} htmlFor="from">
              Başlangıç
            </label>
            <input
              id="from"
              type="date"
              className={adminStyles.input}
              value={from}
              max={to}
              onChange={(event) => setFrom(event.target.value)}
            />
          </div>
          <div className={adminStyles.field}>
            <label className={adminStyles.label} htmlFor="to">
              Bitiş
            </label>
            <input
              id="to"
              type="date"
              className={adminStyles.input}
              value={to}
              min={from}
              onChange={(event) => setTo(event.target.value)}
            />
          </div>
          <Button type="submit">Uygula</Button>
        </form>

        {error ? <p className={adminStyles.error}>{error}</p> : null}

        {loading ? (
          <p className={adminStyles.empty}>Yükleniyor…</p>
        ) : !report ? null : (
          <>
            <div className={styles.statGrid}>
              <StatCard label="Brüt satış" value={formatPriceMinorUnits(report.grossSalesMinorUnits)} />
              <StatCard label="Net satış" value={formatPriceMinorUnits(report.netSalesMinorUnits)} />
              <StatCard label="Refund toplamı" value={formatPriceMinorUnits(report.refundTotalMinorUnits)} />
              <StatCard label="Sipariş sayısı" value={String(report.orderCount)} />
              <StatCard label="Kabul edilen" value={String(report.acceptedOrderCount)} />
              <StatCard label="Reddedilen" value={String(report.rejectedOrderCount)} />
              <StatCard label="Ortalama sepet" value={formatPriceMinorUnits(report.averageOrderValueMinorUnits)} />
              <StatCard label="Masa ziyareti" value={String(report.tableVisitCount)} />
            </div>

            <section className={adminStyles.section}>
              <h2 className={adminStyles.sectionTitle}>Ürün Bazında Satış</h2>
              {report.productBreakdown.length === 0 ? (
                <p className={adminStyles.empty}>Bu aralıkta satış yok.</p>
              ) : (
                <div className={styles.tableWrap}>
                  <table className={styles.table}>
                    <thead>
                      <tr>
                        <th>Ürün</th>
                        <th>Adet</th>
                        <th>Ciro</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.productBreakdown.map((row) => (
                        <tr key={row.productId}>
                          <td>{row.productName}</td>
                          <td>{row.quantitySold}</td>
                          <td>{formatPriceMinorUnits(row.revenueMinorUnits)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className={adminStyles.section}>
              <h2 className={adminStyles.sectionTitle}>Kategori Bazında Ciro</h2>
              {report.categoryBreakdown.length === 0 ? (
                <p className={adminStyles.empty}>Bu aralıkta satış yok.</p>
              ) : (
                <div className={styles.tableWrap}>
                  <table className={styles.table}>
                    <thead>
                      <tr>
                        <th>Kategori</th>
                        <th>Ciro</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.categoryBreakdown.map((row) => (
                        <tr key={row.categoryId}>
                          <td>{row.categoryName}</td>
                          <td>{formatPriceMinorUnits(row.revenueMinorUnits)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className={adminStyles.section}>
              <h2 className={adminStyles.sectionTitle}>Saatlik Dağılım</h2>
              <div className={styles.tableWrap}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>Saat</th>
                      <th>Sipariş</th>
                      <th>Ciro</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.hourlyDistribution
                      .filter((row) => row.orderCount > 0)
                      .map((row) => (
                        <tr key={row.hourOfDay}>
                          <td>{String(row.hourOfDay).padStart(2, "0")}:00</td>
                          <td>{row.orderCount}</td>
                          <td>{formatPriceMinorUnits(row.revenueMinorUnits)}</td>
                        </tr>
                      ))}
                  </tbody>
                </table>
                {report.hourlyDistribution.every((row) => row.orderCount === 0) ? (
                  <p className={adminStyles.empty}>Bu aralıkta satış yok.</p>
                ) : null}
              </div>
            </section>
          </>
        )}
      </main>
    </>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className={styles.statCard}>
      <span className={styles.statLabel}>{label}</span>
      <span className={styles.statValue}>{value}</span>
    </div>
  );
}
