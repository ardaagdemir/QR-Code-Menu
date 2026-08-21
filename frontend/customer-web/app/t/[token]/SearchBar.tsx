"use client";

import Input from "@/components/ui/Input";
import styles from "./SearchBar.module.css";

type Props = {
  value: string;
  onChange: (value: string) => void;
};

export default function SearchBar({ value, onChange }: Props) {
  return (
    <div className={styles.wrapper}>
      <svg className={styles.icon} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} aria-hidden="true">
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-3.4-3.4" strokeLinecap="round" />
      </svg>
      <Input
        type="search"
        inputMode="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder="Ürün ara…"
        aria-label="Menüde ürün ara"
        className={styles.input}
      />
    </div>
  );
}
