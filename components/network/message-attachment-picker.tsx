"use client";
import { useRef, useState } from "react";
import { FileText, ImagePlus, Paperclip, X } from "lucide-react";
import { checkMessageAttachmentMetadata } from "@/lib/network/attachment-policy";


export function MessageAttachmentPicker() {
  const input = useRef<HTMLInputElement>(null);
  const [selected, setSelected] = useState<File | null>(null);
  const [error, setError] = useState("");
  const choose = (file?: File) => {
    if (!file) return;
    const issue = checkMessageAttachmentMetadata(file);
    if (issue) {
      setError(issue);
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
      <label htmlFor="professional-attachment"><Paperclip size={16} aria-hidden="true" /> Share a project image or PDF document (maximum 5 MB)</label>
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
