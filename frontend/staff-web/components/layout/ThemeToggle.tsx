"use client";

import { useSyncExternalStore } from "react";
import { Moon, Sun } from "lucide-react";
import IconButton from "@/components/ui/IconButton";
import { applyTheme, getServerThemeSnapshot, getThemeSnapshot, subscribeTheme } from "@/lib/theme";

export default function ThemeToggle() {
  const theme = useSyncExternalStore(subscribeTheme, getThemeSnapshot, getServerThemeSnapshot);

  function toggle() {
    applyTheme(theme === "dark" ? "light" : "dark");
  }

  return (
    <IconButton
      aria-label={theme === "dark" ? "Aydınlık temaya geç" : "Karanlık temaya geç"}
      size="sm"
      onClick={toggle}
    >
      {theme === "dark" ? <Sun size={16} /> : <Moon size={16} />}
    </IconButton>
  );
}
