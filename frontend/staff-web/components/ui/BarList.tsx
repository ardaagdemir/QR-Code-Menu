import styles from "./BarList.module.css";

export type BarListItem = {
  key: string;
  label: string;
  labelTitle?: string;
  value: number;
  valueLabel: string;
};

type Props = {
  items: BarListItem[];
};

/**
 * Bölüm 19.3 "Raporlama": "gelir trendi", "ürün/kategori ranking", "branch comparison"
 * aynı bilgi hiyerarşisinde sunulur - "ağır bir framework eklenmesi zorunlu değildir".
 * CSS genişlikli bar'lar (en büyük değer = %100) tek amaçlı, bağımlılıksız bir çözüm;
 * çağıran taraf sıralamayı (ranking için değere göre, trend için kronolojik) kendi verir.
 */
export default function BarList({ items }: Props) {
  const max = Math.max(1, ...items.map((item) => item.value));

  return (
    <ul className={styles.list}>
      {items.map((item) => (
        <li key={item.key} className={styles.row}>
          <span className={styles.label} title={item.labelTitle}>{item.label}</span>
          <span className={styles.track}>
            <span className={styles.bar} style={{ width: `${Math.max(2, (item.value / max) * 100)}%` }} />
          </span>
          <span className={styles.value}>{item.valueLabel}</span>
        </li>
      ))}
    </ul>
  );
}
