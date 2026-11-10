"use client";
import { startTransition, useActionState, useEffect, useRef } from "react";
import { useFormStatus } from "react-dom";
import type { SocialState } from "@/lib/social/actions";
export type SocialAction = (
  state: SocialState,
  data: FormData,
) => Promise<SocialState>;
export function Submit({
  children,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const { pending } = useFormStatus();
  return (
    <button {...props} disabled={pending || props.disabled} aria-busy={pending}>
      {pending ? "Please wait…" : children}
    </button>
  );
}
export function ActionForm({
  action,
  fields,
  children,
  confirm,
  reset = false,
}: {
  action: SocialAction;
  fields: Record<string, string>;
  children: React.ReactNode;
  confirm?: string;
  reset?: boolean;
}) {
  const [state, submit, pending] = useActionState(action, {});
  const ref = useRef<HTMLFormElement>(null);
  useEffect(() => {
    if (reset && state.success) ref.current?.reset();
  }, [reset, state]);
  return (
    <form
      ref={ref}
      onSubmit={(e) => {
        e.preventDefault();
        if (pending || (confirm && !window.confirm(confirm))) return;
        const data = new FormData(e.currentTarget);
        startTransition(() => submit(data));
      }}
      aria-busy={pending}
    >
      {Object.entries(fields).map(([name, value]) => (
        <input key={name} type="hidden" name={name} value={value} />
      ))}
      <fieldset disabled={pending} style={{ display: "contents" }}>
        {children}
      </fieldset>
      {state.error && (
        <p className="social-error" role="alert">
          {state.error}
        </p>
      )}
      {reset && state.success && (
        <p role="status" className="social-status">
          {state.success}
        </p>
      )}
    </form>
  );
}
