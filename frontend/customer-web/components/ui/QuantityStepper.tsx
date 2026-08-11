"use client";

import styles from "./QuantityStepper.module.css";

type Props = {
  value: number;
  onChange: (next: number) => void;
  min?: number;
  max?: number;
};

export default function QuantityStepper({ value, onChange, min = 1, max = 99 }: Props) {
  return (
    <div className={styles.stepper}>
      <button
        type="button"
        className={styles.button}
        onClick={() => onChange(Math.max(min, value - 1))}
        disabled={value <= min}
        aria-label="Adedi azalt"
      >
        −
      </button>
      <span className={styles.value} aria-live="polite">
        {value}
      </span>
      <button
        type="button"
        className={styles.button}
        onClick={() => onChange(Math.min(max, value + 1))}
        disabled={value >= max}
        aria-label="Adedi artır"
      >
        +
      </button>
    </div>
  );
}
