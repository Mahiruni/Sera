import type { SVGProps } from "react";
export type SeraReactionKind = "like" | "comment" | "repost" | "share" | "save";
/** One consistent 24px optical grid for every social reaction. */
export function SeraReactionIcon({ kind, active = false, ...props }: SVGProps<SVGSVGElement> & { kind: SeraReactionKind; active?: boolean }) {
  return (
    <svg {...props} viewBox="0 0 24 24" width="23" height="23" aria-hidden="true" focusable="false"
      className={["sera-signature-glyph", props.className].filter(Boolean).join(" ")}
      fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      {kind === "like" && <path fill={active ? "currentColor" : "none"} d="M12 20.4 4.7 13.4C.3 9.3 3.2 3.2 7.6 3.9c1.8.2 3.1 1.2 4.4 2.6 1.3-1.4 2.7-2.4 4.4-2.6 4.4-.7 7.3 5.4 2.9 9.5L12 20.4Z" />}
      {kind === "comment" && <><path d="M20.3 11.7c0 4.9-3.7 8.1-8.4 8.1-1.2 0-2.4-.2-3.5-.6L4 20l.8-4.1a8.3 8.3 0 0 1-1.1-4.2C3.7 6.8 7.4 3.4 12 3.4c4.8 0 8.3 3.5 8.3 8.3Z"/><path d="M8.5 11.8h7" opacity=".72"/></>}
      {kind === "repost" && <><path d="M4 8.1V6.6c0-1.1.9-2 2-2h10.3l-2.5-2.4M16.3 4.6 13.8 7"/><path d="M20 15.9v1.5c0 1.1-.9 2-2 2H7.7l2.5 2.4M7.7 19.4l2.5-2.4"/><path d="M4 12.2v4M20 11.8v-4"/></>}
      {kind === "share" && <><path d="M12 15.6V3.3m0 0L7.7 7.6M12 3.3l4.3 4.3"/><path d="M5.7 12.1H4.2v6.5a2 2 0 0 0 2 2h11.6a2 2 0 0 0 2-2v-6.5h-1.5"/></>}
      {kind === "save" && <path fill={active ? "currentColor" : "none"} d="M6.4 3.6h11.2v16.8L12 16.8l-5.6 3.6V3.6Z" />}
    </svg>
  );
}
