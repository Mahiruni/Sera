"use client";

import { useState } from "react";
import { createPost } from "@/lib/social/actions";

export function PostComposer() {
  const [type, setType] = useState<string>("text");
  const [loading, setLoading] = useState(false);

  return (
    <div className="sera-composer-card">
      <div className="composer-tabs">
        {(['text', 'quote', 'talent', 'project', 'achievement', 'discussion'] as const).map((t) => (
          <button
            key={t}
            type="button"
            className={`tab-btn ${type === t ? "active" : ""}`}
            onClick={() => setType(t)}
          >
            {t.charAt(0).toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      <form action={createPost} onSubmit={() => setLoading(true)} className="composer-form">
        <input type="hidden" name="type" value={type} />

        {type !== 'quote' && type !== 'text' && (
          <input
            type="text"
            name="title"
            placeholder={type === 'project' ? "Project Name & Subtitle" : "Post Title / Headline"}
            className="composer-input"
            required={type === 'project' || type === 'talent'}
          />
        )}

        <textarea
          name="content"
          rows={4}
          placeholder={
            type === 'quote'
              ? "Share an inspiring professional quote..."
              : type === 'talent'
              ? "Describe your creative or technical talent..."
              : type === 'project'
              ? "Detail your project architecture, goals, and impact..."
              : "Share your professional ideas, knowledge, or updates..."
          }
          className="composer-textarea"
          required
        />

        <div className="composer-grid">
          <input
            type="text"
            name="image_url"
            placeholder="Image URL (optional)"
            className="composer-input"
          />
          <input
            type="text"
            name="category"
            placeholder="Category (e.g. Engineering, Design, Finance)"
            className="composer-input"
          />
          <input
            type="text"
            name="location"
            placeholder="Location (e.g. Addis Ababa)"
            className="composer-input"
          />
          <input
            type="text"
            name="skills"
            placeholder="Skills (comma separated, e.g. React, Python, UI/UX)"
            className="composer-input"
          />
        </div>

        <div className="composer-footer">
          <button type="submit" className="button primary" disabled={loading}>
            {loading ? "Publishing..." : "Publish to SERA.et Feed"}
          </button>
        </div>
      </form>
    </div>
  );
}
