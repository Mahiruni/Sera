"use client";
import { useState } from "react";

export function MemberAvatar({
  name,
  photo,
  size = 36,
}: {
  name: string;
  photo?: string | null;
  size?: number;
}) {
  const [failed, setFailed] = useState<string>();
  const initials = name
    .trim()
    .split(/\s+/u)
    .slice(0, 2)
    .map((word) => Array.from(word)[0])
    .join("")
    .toLocaleUpperCase();
  return (
    <span
      className="member-photo"
      aria-hidden="true"
      style={{ width: size, height: size }}
    >
      {initials || "S"}
      {photo && photo !== failed && (
        // Signed images stay on the private storage origin.
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={photo}
          alt=""
          width={size}
          height={size}
          loading="lazy"
          referrerPolicy="no-referrer"
          onError={() => setFailed(photo)}
        />
      )}
    </span>
  );
}
