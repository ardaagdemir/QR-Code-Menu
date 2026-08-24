import type { InputHTMLAttributes, ReactNode } from "react";
import styles from "./Input.module.css";

type Props = InputHTMLAttributes<HTMLInputElement> & {
  invalid?: boolean;
  icon?: ReactNode;
  /** Extra class for the wrapping element when `icon` is set - lets a page size/position
   *  the search bar itself without touching icon/padding, which stay owned by this component. */
  wrapperClassName?: string;
};

/** Copied from customer-web/components/ui/Input.tsx (Bölüm 14/19.1: shared
 * token/component approach). */
export default function Input({ className, invalid, icon, wrapperClassName, ...rest }: Props) {
  const classes = [styles.input, invalid ? styles.invalid : null, icon ? styles.hasIcon : null, className]
    .filter(Boolean)
    .join(" ");
  const input = <input className={classes} {...rest} />;
  if (!icon) {
    return input;
  }
  /* Icon position + the matching input padding both live in Input.module.css so cascade
   * order between this and a page's own CSS module can never flip which one wins. */
  return (
    <div className={[styles.iconWrap, wrapperClassName].filter(Boolean).join(" ")}>
      <span className={styles.icon} aria-hidden="true">
        {icon}
      </span>
      {input}
    </div>
  );
}
