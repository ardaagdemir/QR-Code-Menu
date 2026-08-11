import Skeleton from "@/components/ui/Skeleton";
import styles from "./MenuSkeleton.module.css";

/** Mimics the loaded layout (header + category chips + product cards) so the page
 * doesn't jump once real data arrives (Bölüm 14: "loading / skeleton" durumu). */
export default function MenuSkeleton() {
  return (
    <div className={styles.container} aria-hidden="true">
      <div className={styles.header}>
        <Skeleton width="55%" height="1.5rem" />
        <Skeleton width="35%" height="1rem" />
      </div>
      <div className={styles.nav}>
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} width="90px" height="36px" radius="999px" />
        ))}
      </div>
      <div className={styles.list}>
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className={styles.card}>
            <Skeleton width="84px" height="84px" radius="12px" />
            <div className={styles.cardBody}>
              <Skeleton width="65%" height="1rem" />
              <Skeleton width="35%" height="0.85rem" />
              <Skeleton width="90%" height="0.8rem" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
