"use client";
import { useState } from "react";

/** A single photo-first identity treatment for posts, replies and member cards.
 * Broken/expired signed URLs gracefully fall back to initials.
 */
export function MemberAvatar({
  name,
  photo,
  size = 36,
}: {
  name: string;
  photo?: string | null;
  size?: number;
}) {
  const [failed, setFailed] = useState<string | null>(null);
  const displayName = name.trim() || "Sera member";
  const initials = displayName
    .split(/\s+/u)
    .slice(0, 2)
    .map((word) => Array.from(word)[0] || "")
    .join("")
    .toLocaleUpperCase();
  const validPhoto = typeof photo === "string" && /^https:\/\//i.test(photo)
    ? photo
    : null;
  return (
    <span
      className="member-photo"
      aria-hidden="true"
      style={{ width: size, height: size, flexShrink: 0 }}
    >
      {initials || "S"}
      {validPhoto && validPhoto !== failed && (
        // Signed images stay on the private storage origin.
        // eslint-disable-next-line @next/next/no-img-element
        <img
          key={validPhoto}
          src={validPhoto}
          alt=""
          width={size}
          height={size}
          loading="lazy"
          decoding="async"
          referrerPolicy="no-referrer"
          onError={() => setFailed(validPhoto)}
        />
      )}
    </span>
  );
}
