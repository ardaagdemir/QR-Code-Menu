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

/** Icon-only action button (close/retry/etc). Always requires aria-label since there's
 * no visible text next to the icon (Bölüm 19.1: erişilebilirlik). `size="md"` meets the
 * 44px touch target minimum; `sm` is for dense, mouse-first contexts (e.g. admin table
 * row actions) and should not be used for primary touch actions. */
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
