"use client";

import { useState } from "react";
import { branchCalendarDate, localIsoDate } from "@/lib/time";
import FormField from "./FormField";
import Input from "./Input";
import styles from "./DateRangePresets.module.css";

export type DateRange = { from: string; to: string };

const toIsoDate = localIsoDate;

function startOfWeek(today: Date): Date {
  const day = today.getDay();
  const diff = day === 0 ? 6 : day - 1;
  const monday = new Date(today);
  monday.setDate(today.getDate() - diff);
  return monday;
}

/**
 * Bölüm 19.3 "Raporlama": "hızlı tarih presetleri: Bugün / Dün / Bu Hafta / Bu Ay / Özel".
 * `timeZone` should be the active branch's own timezone (StaffContext.activeBranchTimeZone)
 * so these agree with the branch-timezone-based reporting endpoints they feed - omitting it
 * falls back to the device's own local date (see lib/time.ts's branchIsoDate).
 */
export function presetRange(preset: "today" | "yesterday" | "week" | "month", timeZone?: string | null): DateRange {
  const today = branchCalendarDate(timeZone);
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

type PresetKey = "today" | "yesterday" | "week" | "month";
type SelectedKey = PresetKey | "custom";

const PRESETS: Array<{ key: PresetKey; label: string }> = [
  { key: "today", label: "Bugün" },
  { key: "yesterday", label: "Dün" },
  { key: "week", label: "Bu Hafta" },
  { key: "month", label: "Bu Ay" },
];

type Props = {
  value: DateRange;
  onChange: (range: DateRange) => void;
  /** Active branch's timezone (StaffContext.activeBranchTimeZone) - see presetRange. */
  timeZone?: string | null;
};

export default function DateRangePresets({ value, onChange, timeZone }: Props) {
  // Which button is "active" is tracked explicitly rather than re-derived from `value`
  // on every render: on a Monday, "Bu Hafta"'s range is identical to "Bugün"'s (the week
  // just started), so matching by date range alone can't tell the two apart.
  const matchedPreset = PRESETS.find((preset) => {
    const range = presetRange(preset.key, timeZone);
    return range.from === value.from && range.to === value.to;
  });
  const [selectedKey, setSelectedKey] = useState<SelectedKey>(matchedPreset?.key ?? "custom");
  const [customOpen, setCustomOpen] = useState(selectedKey === "custom");

  function handlePresetClick(key: PresetKey) {
    setSelectedKey(key);
    setCustomOpen(false);
    onChange(presetRange(key, timeZone));
  }

  function handleCustomClick() {
    setSelectedKey("custom");
    setCustomOpen(true);
  }

  function handleCustomChange(nextRange: DateRange) {
    setSelectedKey("custom");
    setCustomOpen(true);
    onChange(nextRange);
  }

  return (
    <div className={styles.wrap}>
      <div className={styles.presets}>
        {PRESETS.map((preset) => (
          <button
            key={preset.key}
            type="button"
            className={[styles.presetButton, selectedKey === preset.key ? styles.active : ""].join(" ")}
            onClick={() => handlePresetClick(preset.key)}
          >
            {preset.label}
          </button>
        ))}
        <button
          type="button"
          className={[styles.presetButton, selectedKey === "custom" ? styles.active : ""].join(" ")}
          onClick={handleCustomClick}
        >
          Özel
        </button>
      </div>
      {customOpen ? (
        <div className={styles.customFields}>
          <FormField label="Başlangıç">
            {(controlProps) => (
              <Input
                {...controlProps}
                type="date"
                value={value.from}
                max={value.to}
                onChange={(event) => handleCustomChange({ ...value, from: event.target.value })}
              />
            )}
          </FormField>
          <FormField label="Bitiş">
            {(controlProps) => (
              <Input
                {...controlProps}
                type="date"
                value={value.to}
                min={value.from}
                onChange={(event) => handleCustomChange({ ...value, to: event.target.value })}
              />
            )}
          </FormField>
        </div>
      ) : null}
    </div>
  );
}
