"use client";
import { useEffect, useRef, useState } from "react";
import Image from "next/image";
import { ChevronLeft, ChevronRight, X } from "lucide-react";

type Photo = { url: string; alt: string };

export function PostMedia({ images }: { images: Photo[] }) {
  const [selected, setSelected] = useState<number | null>(null);
  const previousFocus = useRef<HTMLElement | null>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const openPhoto = (index: number) => {
    previousFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setSelected(index);
  };
  useEffect(() => {
    if (selected === null) return;
    closeButton.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Tab") {
        const dialog = closeButton.current?.closest(".social-lightbox");
        const buttons = Array.from(dialog?.querySelectorAll<HTMLButtonElement>("button:not([disabled])") ?? []);
        const first = buttons[0];
        const last = buttons[buttons.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      }
      if (event.key === "Escape") setSelected(null);
      if (event.key === "ArrowRight") setSelected((n) => n === null ? null : (n + 1) % images.length);
      if (event.key === "ArrowLeft") setSelected((n) => n === null ? null : (n - 1 + images.length) % images.length);
    };
    document.addEventListener("keydown", onKey);
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = oldOverflow;
      previousFocus.current?.focus();
    };
  }, [selected, images.length]);

  if (!images.length) return null;
  return (
    <>
      <div className={`social-images ${images.length > 1 ? "social-images-grid" : ""} ${images.length === 3 ? "social-images-three" : ""} ${images.length >= 5 ? "social-images-many" : ""}`}>
        {images.slice(0, 4).map((photo, index) => (
          <button type="button" className="social-image-link" key={photo.url + index}
            aria-label={`View photo ${index + 1} of ${images.length}`}
            onClick={() => openPhoto(index)}>
            <Image unoptimized src={photo.url} alt={photo.alt} width={1200} height={900}
              style={{ width: "100%", height: "auto", aspectRatio: "auto" }} />
            {images.length > 4 && index === 3 && <span className="social-more-images">+{images.length - 4}</span>}
          </button>
        ))}
      </div>
      {selected !== null && (
        <div className="social-lightbox" role="dialog" aria-modal="true" aria-label="Post photo viewer"
          onClick={(event) => { if (event.target === event.currentTarget) setSelected(null); }}>
          <button type="button" ref={closeButton} className="social-lightbox-close" aria-label="Close photo viewer" onClick={() => setSelected(null)}><X /></button>
          {images.length > 1 && <button type="button" className="social-lightbox-prev" aria-label="Previous photo" onClick={() => setSelected((selected - 1 + images.length) % images.length)}><ChevronLeft /></button>}
          <Image unoptimized className="social-lightbox-photo" src={images[selected].url} alt={images[selected].alt} width={1600} height={1200} style={{ width: "auto", height: "auto" }} />
          {images.length > 1 && <button type="button" className="social-lightbox-next" aria-label="Next photo" onClick={() => setSelected((selected + 1) % images.length)}><ChevronRight /></button>}
          <span className="social-lightbox-count">{selected + 1} / {images.length}</span>
        </div>
      )}
    </>
  );
}

export function ExpandableCaption({ text }: { text: string }) {
  const [expanded, setExpanded] = useState(false);
  const long = text.length > 260;
  return (
    <div className="social-caption">
      <p className="social-body">{long && !expanded ? text.slice(0, 260).trimEnd() + "…" : text}</p>
      {long && <button type="button" className="social-caption-toggle" aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>{expanded ? "See less" : "See more"}</button>}
    </div>
  );
}
