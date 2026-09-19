import styles from "./OrderStatusTimeline.module.css";

type Props = {
  status: string;
};

type StepState = "done" | "current" | "upcoming";

const HAPPY_PATH_STEPS: { key: string; label: string }[] = [
  { key: "PAYMENT", label: "Ödeme" },
  { key: "STORE_ACCEPTANCE", label: "İşletme onayı" },
  { key: "IN_KITCHEN", label: "Hazırlanıyor" },
  { key: "READY", label: "Hazır" },
  { key: "COMPLETED", label: "Tamamlandı" },
];

const KITCHEN_STEP_INDEX = 2;

/** null dönerse sipariş happy path'te değildir (REJECTED_BY_STORE/CANCELLED) - ayrı bir
 * "durduruldu" durumu olarak gösterilir, adımların bir devamı gibi değil. */
function stepIndexForStatus(status: string): number | null {
  switch (status) {
    case "DRAFT":
    case "AWAITING_PAYMENT":
    case "PAYMENT_FAILED":
      return 0;
    case "AWAITING_STORE_ACCEPTANCE":
      return 1;
    case "IN_KITCHEN":
      return 2;
    case "READY":
      return 3;
    case "COMPLETED":
      return 4;
    default:
      return null;
  }
}

const STOPPED_LABELS: Record<string, string> = {
  REJECTED_BY_STORE: "Siparişiniz işletme tarafından reddedildi",
  CANCELLED: "Sipariş iptal edildi",
};

/**
 * Sipariş durumunun salt metin/badge yerine bir ilerleme çizgisi olarak gösterilmesi
 * (docs/product-requirements.md Bölüm 19.2: "anlamlı durum kartı/timeline/progress
 * pattern'i"). Sunucudan yalnızca güncel status geldiği için (ara adım zaman damgası yok)
 * adımlar geçmiş/şimdi/gelecek olarak türetiliyor, gerçek zamanlamalar gösterilmiyor.
 */
export default function OrderStatusTimeline({ status }: Props) {
  const currentIndex = stepIndexForStatus(status);

  if (currentIndex === null) {
    return (
      <div className={styles.stopped} role="status">
        <span className={styles.stoppedDot} aria-hidden="true" />
        <span className={styles.stoppedLabel}>{STOPPED_LABELS[status] ?? status}</span>
      </div>
    );
  }

  // Ödeme / İşletme onayı aşamalarında şu anki adım henüz tamamlanmadı, kendisi yanıp söner.
  // Mutfağa geçildikten sonra (Hazırlanıyor, Hazır, Tamamlandı) ulaşılan adım yeşil olur ve
  // yanıp sönen, bir sonraki bekleyen adımdır; Tamamlandı'da bekleyen adım kalmaz.
  const reachedKitchen = currentIndex >= KITCHEN_STEP_INDEX;
  const doneThroughIndex = reachedKitchen ? currentIndex : currentIndex - 1;
  const pulseIndex = reachedKitchen ? (currentIndex < HAPPY_PATH_STEPS.length - 1 ? currentIndex + 1 : null) : currentIndex;

  return (
    <ol className={styles.timeline}>
      {HAPPY_PATH_STEPS.map((step, index) => {
        const state: StepState = index <= doneThroughIndex ? "done" : index === pulseIndex ? "current" : "upcoming";
        const isError = status === "PAYMENT_FAILED" && index === currentIndex;
        return (
          <li
            key={step.key}
            className={[styles.step, styles[state], isError ? styles.error : ""].filter(Boolean).join(" ")}
          >
            <span className={styles.markerColumn} aria-hidden="true">
              <span className={styles.marker}>
                {state === "done" ? (
                  <svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={2}>
                    <path d="M3 8.5 6.5 12 13 4" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                ) : (
                  index + 1
                )}
              </span>
              {index < HAPPY_PATH_STEPS.length - 1 ? <span className={styles.connector} /> : null}
            </span>
            <span className={styles.label}>
              {step.label}
              {isError ? <span className={styles.errorNote}> · başarısız oldu, tekrar denenebilir</span> : null}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
