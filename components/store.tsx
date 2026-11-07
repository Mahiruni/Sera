"use client";
import {
  createContext,
  useContext,
  useEffect,
  useState,
  useSyncExternalStore,
  type ReactNode,
  type Dispatch,
  type SetStateAction,
} from "react";
import { initialState, isState, type State, type Lang } from "@/lib/model";
import { usePathname } from "next/navigation";
import {
  isLocale,
  contentLanguage,
  pageLanguage,
  type LocaleCode,
} from "@/lib/locales";
import { translate, type Key } from "@/lib/i18n";
type Snapshot = {
  state: State;
  lang: Lang;
  locale: LocaleCode;
  ready: boolean;
  storageError: boolean;
};
const serverSnapshot: Snapshot = {
  state: initialState,
  lang: "en",
  locale: "en",
  ready: false,
  storageError: false,
};
const amSnapshot: Snapshot = { ...serverSnapshot, lang: "am", locale: "am" };
let snapshot = serverSnapshot;
let loaded = false;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((fn) => fn());
function readStorage() {
  let state = initialState,
    lang: Lang = "en",
    locale: LocaleCode = "en",
    storageError = false;
  try {
    const raw = localStorage.getItem("sera-next-v1");
    if (raw) {
      const value: unknown = JSON.parse(raw);
      if (isState(value)) state = value;
      else storageError = true;
    }
    const choice = localStorage.getItem("sera-language");
    if (isLocale(choice)) {
      locale = choice;
      lang = contentLanguage(choice);
    }
  } catch {
    storageError = true;
  }
  snapshot = { state, lang, locale, ready: true, storageError };
  loaded = true;
}
function getSnapshot() {
  if (!loaded) readStorage();
  return snapshot;
}
function subscribe(listener: () => void) {
  listeners.add(listener);
  const sync = (e: StorageEvent) => {
    if (
      e.key === null ||
      e.key === "sera-next-v1" ||
      e.key === "sera-language"
    ) {
      readStorage();
      emit();
    }
  };
  window.addEventListener("storage", sync);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", sync);
  };
}
function persist() {
  try {
    localStorage.setItem("sera-next-v1", JSON.stringify(snapshot.state));
    localStorage.setItem("sera-language", snapshot.locale);
  } catch {
    snapshot = { ...snapshot, storageError: true };
  }
  emit();
}
const setState: Dispatch<SetStateAction<State>> = (action) => {
  getSnapshot();
  snapshot = {
    ...snapshot,
    state: typeof action === "function" ? action(snapshot.state) : action,
  };
  persist();
};
const setLang = (locale: LocaleCode) => {
  getSnapshot();
  snapshot = { ...snapshot, locale, lang: contentLanguage(locale) };
  persist();
};
type Context = Snapshot & {
  setState: typeof setState;
  setLang: typeof setLang;
  t: (key: Key) => string;
  notice: string;
  notify: (text: string) => void;
};
const Store = createContext<Context | null>(null);
export function Provider({
  children,
  initialLocale = "en",
}: {
  children: ReactNode;
  initialLocale?: LocaleCode;
}) {
  const pathname = usePathname();
  const value = useSyncExternalStore(subscribe, getSnapshot, () =>
    initialLocale === "am" ? amSnapshot : serverSnapshot,
  );
  const [notice, notify] = useState("");
  const htmlLanguage = pageLanguage(pathname, value.locale);
  const lang: Lang = htmlLanguage === "am" ? "am" : "en";
  useEffect(() => {
    document.documentElement.lang = htmlLanguage;
    document.documentElement.dir = htmlLanguage === "ar" ? "rtl" : "ltr";
  }, [htmlLanguage]);
  useEffect(() => {
    if (!notice) return;
    const id = setTimeout(() => notify(""), 4500);
    return () => clearTimeout(id);
  }, [notice]);
  return (
    <Store.Provider
      value={{
        ...value,
        lang,
        setState,
        setLang,
        t: (k) => translate(lang, k),
        notice,
        notify,
      }}
    >
      {children}
    </Store.Provider>
  );
}
export function useSera() {
  const ctx = useContext(Store);
  if (!ctx) throw Error("Missing Sera provider");
  return ctx;
}
