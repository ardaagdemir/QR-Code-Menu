"use client";

import FormField from "./FormField";
import Input from "./Input";
import styles from "./DateRangePresets.module.css";

export type DateRange = { from: string; to: string };

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function startOfWeek(today: Date): Date {
  const day = today.getDay();
  const diff = day === 0 ? 6 : day - 1;
  const monday = new Date(today);
  monday.setDate(today.getDate() - diff);
  return monday;
}

/** Bölüm 19.3 "Raporlama": "hızlı tarih presetleri: Bugün / Dün / Bu Hafta / Bu Ay / Özel". */
export function presetRange(preset: "today" | "yesterday" | "week" | "month"): DateRange {
  const today = new Date();
  if (preset === "today") {
    return { from: toIsoDate(today), to: toIsoDate(today) };
  }
  if (preset === "yesterday") {
    const yesterday = new Date(today);
    yesterday.setDate(today.getDate() - 1);
    return { from: toIsoDate(yesterday), to: toIsoDate(yesterday) };
  }
  if (preset === "week") {
    return { from: toIsoDate(startOfWeek(today)), to: toIsoDate(today) };
  }
  return { from: toIsoDate(new Date(today.getFullYear(), today.getMonth(), 1)), to: toIsoDate(today) };
}

const PRESETS: Array<{ key: "today" | "yesterday" | "week" | "month"; label: string }> = [
  { key: "today", label: "Bugün" },
  { key: "yesterday", label: "Dün" },
  { key: "week", label: "Bu Hafta" },
  { key: "month", label: "Bu Ay" },
];

type Props = {
  value: DateRange;
  onChange: (range: DateRange) => void;
};

export default function DateRangePresets({ value, onChange }: Props) {
  const activePreset = PRESETS.find((preset) => {
    const range = presetRange(preset.key);
    return range.from === value.from && range.to === value.to;
  });

  return (
    <div className={styles.wrap}>
      <div className={styles.presets}>
        {PRESETS.map((preset) => (
          <button
            key={preset.key}
            type="button"
            className={[styles.presetButton, activePreset?.key === preset.key ? styles.active : ""].join(" ")}
            onClick={() => onChange(presetRange(preset.key))}
          >
            {preset.label}
          </button>
        ))}
        <span className={[styles.presetButton, !activePreset ? styles.active : ""].join(" ")}>Özel</span>
      </div>
      <div className={styles.customFields}>
        <FormField label="Başlangıç">
          {(controlProps) => (
            <Input {...controlProps} type="date" value={value.from} max={value.to} onChange={(event) => onChange({ ...value, from: event.target.value })} />
          )}
        </FormField>
        <FormField label="Bitiş">
          {(controlProps) => (
            <Input {...controlProps} type="date" value={value.to} min={value.from} onChange={(event) => onChange({ ...value, to: event.target.value })} />
          )}
        </FormField>
      </div>
    </div>
  );
}
