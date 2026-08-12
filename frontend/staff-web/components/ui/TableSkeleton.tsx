import Skeleton from "./Skeleton";
import styles from "./TableSkeleton.module.css";

type Props = {
  rows?: number;
};

/** Table yüklenirken gösterilen satır iskeleti - `Table` kullanan sayfalarda ortak. */
export default function TableSkeleton({ rows = 4 }: Props) {
  return (
    <div className={styles.stack} aria-hidden="true">
      {Array.from({ length: rows }).map((_, index) => (
        <Skeleton key={index} height="2.75rem" radius="var(--radius-md)" />
      ))}
    </div>
  );
}
