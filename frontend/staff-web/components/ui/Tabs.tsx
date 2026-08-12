"use client";

import styles from "./Tabs.module.css";

type TabItem = { id: string; label: string };

type Props = {
  items: TabItem[];
  activeId: string;
  onChange: (id: string) => void;
  ariaLabel: string;
};

/** Copied from customer-web/components/ui/Tabs.tsx (Bölüm 14/19.1: shared
 * token/component approach). */
export default function Tabs({ items, activeId, onChange, ariaLabel }: Props) {
  return (
    <div className={styles.tablist} role="tablist" aria-label={ariaLabel}>
      {items.map((item) => {
        const selected = item.id === activeId;
        return (
          <button
            key={item.id}
            type="button"
            role="tab"
            aria-selected={selected}
            className={[styles.tab, selected ? styles.active : null].filter(Boolean).join(" ")}
            onClick={() => onChange(item.id)}
          >
            {item.label}
          </button>
        );
      })}
    </div>
  );
}
