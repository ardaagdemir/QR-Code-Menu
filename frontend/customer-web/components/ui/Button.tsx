"use client";

import type { ButtonHTMLAttributes, ReactNode } from "react";
import styles from "./Button.module.css";

type Variant = "primary" | "secondary" | "ghost";
type Size = "md" | "lg";

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  size?: Size;
  children: ReactNode;
};

/** Shared button primitive - every button in the app should render through this so
 * variant/size/touch-target rules stay in one place (docs/product-requirements.md
 * Bölüm 14: "tutarlı design tokens / component dili"). */
export default function Button({ variant = "primary", size = "md", className, children, type = "button", ...rest }: Props) {
  const classes = [styles.button, styles[variant], styles[size], className].filter(Boolean).join(" ");
  return (
    <button type={type} className={classes} {...rest}>
      {children}
    </button>
  );
}
