"use client";
import { useSyncExternalStore } from "react";
import { Moon, Sun } from "lucide-react";
function subscribe(callback: () => void) {
  window.addEventListener("sera:theme", callback);
  window.addEventListener("storage", callback);
  return () => {
    window.removeEventListener("sera:theme", callback);
    window.removeEventListener("storage", callback);
  };
}
function snapshot() {
  return document.documentElement.dataset.seraTheme === "dark"
    ? "dark"
    : "light";
}
export function ThemeToggle() {
  const theme = useSyncExternalStore(subscribe, snapshot, () => "light");
  return (
    <button
      type="button"
      className="network-theme"
      aria-label={`Switch to ${theme === "light" ? "dark" : "light"} mode`}
      onClick={() => {
        const next = theme === "light" ? "dark" : "light";
        document.documentElement.dataset.seraTheme = next;
        try {
          localStorage.setItem("sera-theme", next);
        } catch {
          /* The current page still switches when storage is blocked. */
        }
        window.dispatchEvent(new Event("sera:theme"));
      }}
    >
      {theme === "light" ? <Moon size={17} /> : <Sun size={17} />}
      <span>{theme === "light" ? "Dark" : "Light"}</span>
    </button>
  );
}
