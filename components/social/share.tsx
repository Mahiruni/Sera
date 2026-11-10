"use client";
import { useState, useTransition } from "react";
import { Share2 } from "lucide-react";
import { sharePost } from "@/lib/social/actions";
export function ShareButton({
  id,
  title,
  count,
}: {
  id: string;
  title: string;
  count: number;
}) {
  const [message, setMessage] = useState("");
  const [pending, start] = useTransition();
  return (
    <div className="social-share">
      <button
        type="button"
        disabled={pending}
        onClick={() =>
          start(async () => {
            setMessage("");
            const url = new URL("/posts/" + id, window.location.origin).href;
            const nativeShare = typeof navigator.share === "function";
            try {
              if (nativeShare)
                await navigator.share({
                  title: title || "A story on Sera.et",
                  url,
                });
              else {
                await navigator.clipboard.writeText(url);
                setMessage("Link copied. Members sign in to read.");
              }
              const form = new FormData();
              form.set("post_id", id);
              const result = await sharePost({}, form);
              if (result.error)
                setMessage(
                  "Link shared, but the share count could not be updated.",
                );
              else if (nativeShare) setMessage("Shared.");
            } catch (error) {
              if (!(
                error instanceof DOMException && error.name === "AbortError"
              ))
                setMessage(
                  "Could not share. Open the story and copy its address.",
                );
            }
          })
        }
      >
        <Share2 size={17} aria-hidden="true" />
        <span>Share {count > 0 ? count : ""}</span>
      </button>
      {message && <p role="status">{message}</p>}
    </div>
  );
}
