"use client";
import {
  startTransition,
  useActionState,
  useEffect,
  useRef,
  useState,
} from "react";
import Image from "next/image";
import { ImagePlus, Send } from "lucide-react";
import { createPost, updatePost } from "@/lib/social/actions";
import {
  postKinds,
  MEDIA_LIMIT,
  MEDIA_COUNT,
  validMedia,
  type PostKind,
} from "@/lib/social/validation";
import type { SocialPost } from "@/lib/social/data";
import { Submit } from "./forms";
const labels: Record<PostKind, string> = {
  idea: "Idea",
  photos: "Photos",
  quote: "Quote",
  talent: "Talent",
  project: "Project",
  achievement: "Achievement",
  question: "Discussion",
};
export function PostComposer({ post }: { post?: SocialPost }) {
  const [kind, setKind] = useState<PostKind>(post?.kind || "idea");
  const [previews, setPreviews] = useState<string[]>([]);
  const urls = useRef<string[]>([]);
  const [fileError, setFileError] = useState("");
  const ref = useRef<HTMLFormElement>(null);
  const [state, action, pending] = useActionState(
    async (
      previous: import("@/lib/social/actions").SocialState,
      data: FormData,
    ) => {
      const result = await (post ? updatePost : createPost)(previous, data);
      if (result.success && !post) {
        ref.current?.reset();
        urls.current.forEach((url) => URL.revokeObjectURL(url));
        urls.current = [];
        setPreviews([]);
        setFileError("");
      }
      return result;
    },
    {},
  );
  useEffect(
    () => () => urls.current.forEach((url) => URL.revokeObjectURL(url)),
    [],
  );
  return (
    <section
      className="social-composer"
      aria-label={post ? "Edit post" : "Create a post"}
    >
      <div className="social-section-heading">
        <div>
          <p className="eyebrow">YOUR VOICE MATTERS</p>
          <h2>{post ? "Refine your story." : "What are you working on?"}</h2>
        </div>
        <Send size={22} aria-hidden="true" />
      </div>
      <form
        ref={ref}
        onSubmit={(e) => {
          e.preventDefault();
          if (fileError || pending) return;
          const data = new FormData(e.currentTarget);
          startTransition(() => action(data));
        }}
      >
        {post && <input type="hidden" name="post_id" value={post.id} />}
        <input type="hidden" name="kind" value={kind} />
        {!post && (
          <div className="social-kind-tabs" role="group" aria-label="Post type">
            {postKinds.map((k) => (
              <button
                type="button"
                key={k}
                aria-pressed={kind === k}
                onClick={() => setKind(k)}
              >
                {labels[k]}
              </button>
            ))}
          </div>
        )}
        <label>
          Headline {kind === "quote" && "(optional)"}
          <input
            name="title"
            maxLength={140}
            minLength={kind === "quote" ? undefined : 3}
            required={kind !== "quote"}
            defaultValue={post?.title}
            placeholder="Give your story a clear headline"
          />
        </label>
        <label>
          {kind === "quote" ? "Your quote" : "Your story"}
          <textarea
            name="body"
            rows={4}
            maxLength={kind === "quote" ? 600 : 10000}
            minLength={kind === "quote" ? 3 : 1}
            required={kind !== "photos"}
            defaultValue={post?.body}
            placeholder="Share something useful, inspiring or worth discussing…"
          />
        </label>
        {kind === "quote" && (
          <label>
            Attribution
            <input
              name="quote_source"
              maxLength={120}
              defaultValue={post?.quote_source}
              placeholder="Who said it?"
            />
          </label>
        )}
        {kind !== "quote" && (
          <input
            type="hidden"
            name="quote_source"
            value={post?.quote_source || ""}
          />
        )}
        <details
          className="social-composer-details"
          open={post || kind === "photos" ? true : undefined}
        >
          <summary>Add details, skills or photos</summary>
          <div className="social-form-grid">
            <label>
              Field
              <input
                name="category"
                maxLength={60}
                defaultValue={post?.category}
                placeholder="Engineering, design…"
              />
            </label>
            <label>
              Location
              <input
                name="location"
                maxLength={120}
                defaultValue={post?.location}
                placeholder="Addis Ababa"
              />
            </label>
          </div>
          <label>
            Skills <span>(up to 10, separated by commas)</span>
            <input
              name="skills"
              maxLength={400}
              defaultValue={post?.skills.join(", ")}
              placeholder="React, photography, product design"
            />
          </label>
          <label>
            Project link <span>(optional, HTTPS)</span>
            <input
              type="url"
              name="project_url"
              maxLength={500}
              defaultValue={post?.project_url || ""}
              placeholder="https://"
            />
          </label>
          {!post && (
            <label className="social-upload">
              <ImagePlus size={20} /> Add photos{" "}
              <span>
                Up to {MEDIA_COUNT} images · {MEDIA_LIMIT / 1024 / 1024} MB
                total · JPG, PNG, WebP
              </span>
              <input
                type="file"
                name="media"
                multiple
                accept="image/jpeg,image/png,image/webp"
                required={kind === "photos"}
                onChange={(e) => {
                  const chosen = [...(e.target.files || [])];
                  urls.current.forEach((url) => URL.revokeObjectURL(url));
                  urls.current = validMedia(chosen)
                    ? chosen.map((file) => URL.createObjectURL(file))
                    : [];
                  setPreviews(urls.current);
                  setFileError(
                    validMedia(chosen)
                      ? ""
                      : "Choose up to four supported images totaling 3 MB or less.",
                  );
                }}
              />
            </label>
          )}
          {!!previews.length && (
            <div className="social-preview">
              {previews.map((url, i) => (
                <Image
                  unoptimized
                  width={90}
                  height={90}
                  key={url}
                  src={url}
                  alt={`Selected photo ${i + 1}`}
                />
              ))}
            </div>
          )}
        </details>
        {(fileError || state.error) && (
          <p role="alert" className="social-error">
            {fileError || state.error}
          </p>
        )}
        {state.success && (
          <p role="status" className="social-status">
            {state.success}
          </p>
        )}
        <div className="social-composer-bottom">
          <small>Visible to signed-in Sera members.</small>
          <Submit
            className="button primary"
            disabled={Boolean(fileError) || pending}
          >
            {pending ? "Publishing…" : post ? "Save changes" : "Publish story"}
          </Submit>
        </div>
      </form>
    </section>
  );
}
