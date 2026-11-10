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
          aria-label={`${post.author.full_name}'s posts`}
        >
          <MemberAvatar
            name={post.author.full_name}
            photo={post.photo}
            size={42}
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
          className={`social-images ${post.images.length > 1 ? "social-images-grid" : ""}`}
        >
          {post.images.map((img, i) => (
            <a href={img.url} target="_blank" rel="noopener noreferrer" key={i}>
              <Image
                unoptimized
                src={img.url}
                alt={img.alt}
                width={1200}
                height={900}
              />
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
            <Heart size={17} fill={post.liked ? "currentColor" : "none"} />
            <span>
              {post.like_count}{" "}
              <span className="social-action-word">likes</span>
            </span>
          </Submit>
        </ActionForm>
        <Link
          href={"/posts/" + post.id + "#comments"}
          aria-label={`${post.comment_count} comments, open conversation`}
        >
          <MessageCircle size={17} />
          <span>
            {post.comment_count}{" "}
            <span className="social-action-word">comments</span>
          </span>
        </Link>
        <ActionForm
          action={toggleSavePost}
          fields={{ post_id: post.id, remove: post.saved ? "yes" : "no" }}
        >
          <Submit aria-pressed={post.saved}>
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
