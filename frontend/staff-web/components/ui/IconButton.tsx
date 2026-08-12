"use client";

import type { ButtonHTMLAttributes, ReactNode } from "react";
import styles from "./IconButton.module.css";

type Variant = "ghost" | "secondary";
type Size = "sm" | "md";

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  size?: Size;
  "aria-label": string;
  children: ReactNode;
};

/** Copied from customer-web/components/ui/IconButton.tsx (Bölüm 14/19.1: shared
 * token/component approach). */
export default function IconButton({
  variant = "ghost",
  size = "md",
  className,
  children,
  type = "button",
  ...rest
}: Props) {
  const classes = [styles.button, styles[variant], styles[size], className].filter(Boolean).join(" ");
  return (
    <button type={type} className={classes} {...rest}>
      {children}
    </button>
  );
}
