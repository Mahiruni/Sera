import { MemberAvatar } from "@/components/activity/avatar";
import Link from "next/link";
import Image from "next/image";
import {
  Heart,
  MessageCircle,
  Bookmark,
  ArrowUpRight,
  Pencil,
  Trash2,
  ThumbsUp,
} from "lucide-react";
import {
  toggleLikePost,
  toggleSavePost,
  deletePost,
} from "@/lib/social/actions";
import type { SocialPost } from "@/lib/social/data";
import { ActionForm, Submit } from "./forms";
import { ShareButton } from "./share";
export function PostCard({
  post,
  currentUserId,
  detail = false,
}: {
  post: SocialPost;
  currentUserId: string;
  detail?: boolean;
}) {
  const owner = currentUserId === post.author_id;
  return (
    <article className={`social-card social-card-${post.kind}`}>
      <header className="social-post-header">
        <Link
          className="social-avatar"
          href={"/members/" + post.author_id}
          aria-label={`View ${post.author.full_name}'s professional profile`}
        >
          <MemberAvatar
            name={post.author.full_name}
            photo={post.photo}
            size={46}
          />
        </Link>
        <div>
          <Link href={"/members/" + post.author_id} className="social-author">
            {post.author.full_name}
          </Link>
          <p>{post.author.headline || "Sera community member"}</p>
          <small>
            <time dateTime={post.created_at}>
              {new Date(post.created_at).toLocaleDateString("en-GB", {
                timeZone: "Africa/Addis_Ababa",
                day: "numeric",
                month: "short",
                year: "numeric",
              })}
            </time>
            {post.edited_at ? " · Edited" : ""}
          </small>
        </div>
        <span className="social-kind">
          {post.kind === "question" ? "Discussion" : post.kind}
        </span>
      </header>
      {post.title && (
        <h2>
          <Link href={"/posts/" + post.id}>{post.title}</Link>
        </h2>
      )}
      {post.kind === "quote" ? (
        <blockquote>
          <p>{post.body}</p>
          {post.quote_source && <cite>— {post.quote_source}</cite>}
        </blockquote>
      ) : (
        <p className="social-body">{post.body}</p>
      )}
      {!!post.images.length && (
        <div
          className={`social-images ${post.images.length > 1 ? "social-images-grid" : ""} ${post.images.length === 3 ? "social-images-three" : ""} ${post.images.length >= 5 ? "social-images-many" : ""}`}
        >
          {post.images.slice(0, post.images.length > 4 ? 4 : undefined).map((img, i) => (
            <a href={img.url} target="_blank" rel="noopener noreferrer" key={i} className="social-image-link" aria-label={`Open photo ${i + 1} in full size`}>
              <Image
                unoptimized
                src={img.url}
                alt={img.alt}
                width={1200}
                height={900}
                style={{ width: "100%", height: "auto", aspectRatio: "auto" }}
              />
              {post.images.length > 4 && i === 3 && <span className="social-more-images">+{post.images.length - 4}</span>}
            </a>
          ))}
        </div>
      )}
      {post.project_url && (
        <a
          className="social-project"
          href={post.project_url}
          target="_blank"
          rel="noopener noreferrer"
        >
          Explore project <ArrowUpRight size={17} />
        </a>
      )}
      <div className="social-tags">
        {post.category && <span>{post.category}</span>}
        {post.location && <span>{post.location}</span>}
        {post.skills.map((skill) => (
          <span key={skill}>#{skill}</span>
        ))}
      </div>
      <footer className="social-post-actions">
        <ActionForm
          action={toggleLikePost}
          fields={{ post_id: post.id, remove: post.liked ? "yes" : "no" }}
        >
          <Submit
            aria-pressed={post.liked}
            aria-label={`${post.liked ? "Unlike" : "Like"} post, ${post.like_count} likes`}
          >
            <span className="social-reaction-icons" aria-hidden="true"><Heart size={15} fill="currentColor" /><ThumbsUp size={14} fill="currentColor" /></span>
            <span className="social-action-count">{post.like_count}</span>
          </Submit>
        </ActionForm>
        <Link
          href={"/posts/" + post.id + "#comments"}
          aria-label={`${post.comment_count} comments, open conversation`}
        >
          <MessageCircle size={19} />
          <span className="social-action-count">{post.comment_count}</span>
        </Link>
        <ActionForm
          action={toggleSavePost}
          fields={{ post_id: post.id, remove: post.saved ? "yes" : "no" }}
        >
          <Submit aria-pressed={post.saved} aria-label={post.saved ? "Remove saved post" : "Save post"}>
            <Bookmark size={17} fill={post.saved ? "currentColor" : "none"} />
            <span>{post.saved ? "Saved" : "Save"}</span>
          </Submit>
        </ActionForm>
        <ShareButton id={post.id} title={post.title} count={post.share_count} />
      </footer>
      {owner && (
        <details className="social-owner">
          <summary>Manage your post</summary>
          <Link href={"/posts/" + post.id + "/edit"}>
            <Pencil size={15} /> Edit story
          </Link>
          <ActionForm
            action={deletePost}
            fields={{ post_id: post.id, return_to: detail ? "/feed" : "" }}
            confirm="Remove this post and all its comments?"
            reset
          >
            <Submit>
              <Trash2 size={15} /> Delete story
            </Submit>
          </ActionForm>
          {detail && (
            <small>
              Deletion also removes this story from members’ saved posts.
            </small>
          )}
        </details>
      )}
    </article>
  );
}
