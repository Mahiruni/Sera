"use client";
import { useEffect, useRef, useState } from "react";
import Image from "next/image";
import { ChevronLeft, ChevronRight, X } from "lucide-react";

type Photo = { url: string; alt: string; width?:number; height?:number };
const preview=(url:string,w:number)=>/^\/api\/post-media\/[0-9a-f-]{36}$/i.test(url)?url+`?w=${w}`:url;

export function PostMedia({ images }: { images: Photo[] }) {
  const [selected, setSelected] = useState<number | null>(null);
  const previousFocus = useRef<HTMLElement | null>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const openRef = useRef(false);
  const [touchStartX, setTouchStartX] = useState<number | null>(null);
  const openPhoto = (index: number) => {
    previousFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setSelected(index);
  };
  useEffect(() => {
    if (selected === null) return;
    if (!openRef.current) {
      openRef.current = true;
      closeButton.current?.focus();
    }
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
      if (event.key === "ArrowRight" && images.length > 1) setSelected((n) => n === null ? null : (n + 1) % images.length);
      if (event.key === "ArrowLeft" && images.length > 1) setSelected((n) => n === null ? null : (n - 1 + images.length) % images.length);
    };
    document.addEventListener("keydown", onKey);
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = oldOverflow;
      // Restore focus only when the viewer actually closes, not between photos.
    };
  }, [selected, images.length]);

  useEffect(() => {
    if (selected !== null || !openRef.current) return;
    openRef.current = false;
    previousFocus.current?.focus();
  }, [selected]);

  if (!images.length) return null;
  return (
    <>
      <div className={`social-images ${images.length > 1 ? "social-images-grid" : ""} ${images.length === 3 ? "social-images-three" : ""} ${images.length >= 5 ? "social-images-many" : ""}`}>
        {images.slice(0, 4).map((photo, index) => (
          <button type="button" className="social-image-link" key={photo.url + index}
            aria-label={`View photo ${index + 1} of ${images.length}`}
            onClick={() => openPhoto(index)}>
            {/* Authenticated previews use browser srcset; Next Image optimizer cannot forward private cookies. */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={preview(photo.url,800)} srcSet={preview(photo.url,480)!==photo.url?[480,800,1200].map(w=>`${preview(photo.url,w)} ${w}w`).join(", "):undefined}
              sizes={images.length>1?"(max-width: 600px) 48vw, 340px":"(max-width: 720px) 100vw, 680px"}
              alt={photo.alt} width={photo.width||1200} height={photo.height||900} loading="lazy" decoding="async"
              style={{ width: "100%", height: "auto", aspectRatio: "auto" }} />
            {images.length > 4 && index === 3 && <span className="social-more-images">+{images.length - 4}</span>}
          </button>
        ))}
      </div>
      {selected !== null && (
        <div className="social-lightbox" role="dialog" aria-modal="true" aria-label="Post photo viewer"
          onClick={(event) => { if (event.target === event.currentTarget) setSelected(null); }}
          onTouchStart={(event) => setTouchStartX(event.touches[0]?.clientX ?? null)}
          onTouchEnd={(event) => {
            if (touchStartX === null || images.length < 2) return;
            const endX = event.changedTouches[0]?.clientX;
            if (endX === undefined) return;
            const delta = endX - touchStartX;
            if (Math.abs(delta) > 55) setSelected((selected + (delta < 0 ? 1 : images.length - 1)) % images.length);
            setTouchStartX(null);
          }}>
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
  const characters = Array.from(text);
  const long = characters.length > 260;
  return (
    <div className="social-caption">
      <p className="social-body">{long && !expanded ? characters.slice(0, 260).join("").trimEnd() + "…" : text}</p>
      {long && <button type="button" className="social-caption-toggle" aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>{expanded ? "See less" : "See more"}</button>}
    </div>
  );
}
