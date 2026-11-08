"use client";
import { useFormStatus } from "react-dom";
export function WorkspaceSubmit({ children }: { children: React.ReactNode }) {
  const { pending } = useFormStatus();
  return (
    <button className="ws-row-button" disabled={pending}>
      {pending ? "Updating…" : children}
    </button>
  );
}
