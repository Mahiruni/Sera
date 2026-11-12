"use client";
import { useRef, useState } from "react";
import { FileText, ImagePlus, Paperclip, X } from "lucide-react";

const MAX_SIZE = 5 * 1024 * 1024;
const ALLOWED = new Set(["image/jpeg", "image/png", "image/webp", "application/pdf"]);

export function MessageAttachmentPicker() {
  const input = useRef<HTMLInputElement>(null);
  const [selected, setSelected] = useState<File | null>(null);
  const [error, setError] = useState("");
  const choose = (file?: File) => {
    if (!file) return;
    if (!ALLOWED.has(file.type)) {
      setError("Choose a JPG, PNG, WebP or PDF file.");
      if (input.current) input.current.value = "";
      setSelected(null);
      return;
    }
    if (file.size > MAX_SIZE) {
      setError("Files must be 5 MB or smaller.");
      if (input.current) input.current.value = "";
      setSelected(null);
      return;
    }
    setSelected(file);
    setError("");
  };
  const clear = () => {
    if (input.current) input.current.value = "";
    setSelected(null);
    setError("");
  };
  return (
    <div className="sera-message-attachment">
      <label htmlFor="professional-attachment" title="Attach image or PDF (up to 5 MB)"><Paperclip size={22} aria-hidden="true" /><span className="sr-only">Attach image or PDF, up to 5 MB</span></label>
      <input ref={input} id="professional-attachment" name="attachment" type="file"
        accept="image/jpeg,image/png,image/webp,application/pdf"
        onChange={(event) => choose(event.currentTarget.files?.[0])} />
      {selected && <div className="sera-message-attachment-preview" role="status">
        {selected.type === "application/pdf" ? <FileText size={19} /> : <ImagePlus size={19} />}
        <span>{selected.name} · {(selected.size / 1024 / 1024).toFixed(2)} MB</span>
        <button type="button" onClick={clear} aria-label="Remove attachment"><X size={17} /></button>
      </div>}
      {error && <p role="alert" className="sera-message-attachment-error">{error}</p>}
    </div>
  );
}
