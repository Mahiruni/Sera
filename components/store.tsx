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
import { translate, type Key } from "@/lib/i18n";
type Snapshot = {
  state: State;
  lang: Lang;
  ready: boolean;
  storageError: boolean;
};
const serverSnapshot: Snapshot = {
  state: initialState,
  lang: "en",
  ready: false,
  storageError: false,
};
let snapshot = serverSnapshot;
let loaded = false;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((fn) => fn());
function readStorage() {
  let state = initialState,
    lang: Lang = "en",
    storageError = false;
  try {
    const raw = localStorage.getItem("sera-next-v1");
    if (raw) {
      const value: unknown = JSON.parse(raw);
      if (isState(value)) state = value;
      else storageError = true;
    }
    if (localStorage.getItem("sera-language") === "am") lang = "am";
  } catch {
    storageError = true;
  }
  snapshot = { state, lang, ready: true, storageError };
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
    localStorage.setItem("sera-language", snapshot.lang);
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
const setLang = (lang: Lang) => {
  getSnapshot();
  snapshot = { ...snapshot, lang };
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
export function Provider({ children }: { children: ReactNode }) {
  const value = useSyncExternalStore(
    subscribe,
    getSnapshot,
    () => serverSnapshot,
  );
  const [notice, notify] = useState("");
  useEffect(() => {
    document.documentElement.lang = value.lang;
  }, [value.lang]);
  useEffect(() => {
    if (!notice) return;
    const id = setTimeout(() => notify(""), 4500);
    return () => clearTimeout(id);
  }, [notice]);
  return (
    <Store.Provider
      value={{
        ...value,
        setState,
        setLang,
        t: (k) => translate(value.lang, k),
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
