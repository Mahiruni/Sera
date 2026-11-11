import { MemberAvatar } from "@/components/activity/avatar";
import Link from "next/link";
import {
  ArrowUpRight,
  Pencil,
  Trash2,
  MoreHorizontal,
  Flag,
} from "lucide-react";
import {
  toggleLikePost,
  toggleSavePost,
  deletePost,
  reportPost,
} from "@/lib/social/actions";
import type { SocialPost } from "@/lib/social/data";
import { ActionForm, Submit } from "./forms";
import { ShareButton } from "./share";
import { SeraReactionIcon } from "./SeraReactionIcon";
import { ExpandableCaption, PostMedia } from "./post-media";
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
  const normalize = (value: string) => value.replace(/\s+/g, " ").trim().toLocaleLowerCase();
  const title = post.title?.trim() || "";
  const body = post.body?.trim() || "";
  // Legacy posts sometimes repeat the title verbatim at the beginning of the body.
  // Keep the full original body; suppress only a redundant title presentation.
  const showTitle = Boolean(title) && !(normalize(body) === normalize(title) || normalize(body).startsWith(normalize(title) + " "));
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
      {showTitle && (
        <h2>
          <Link href={"/posts/" + post.id}>{title}</Link>
        </h2>
      )}
      {post.kind === "quote" ? (
        <blockquote>
          <p>{post.body}</p>
          {post.quote_source && <cite>— {post.quote_source}</cite>}
        </blockquote>
      ) : (
        <ExpandableCaption text={post.body} />
      )}
      <PostMedia images={post.images} />
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
            <SeraReactionIcon kind="like" active={post.liked} />
            <span className="social-action-count">{post.like_count}</span>
          </Submit>
        </ActionForm>
        <Link
          href={"/posts/" + post.id + "#comments"}
          aria-label={`${post.comment_count} comments, open conversation`}
        >
          <SeraReactionIcon kind="comment" />
          <span className="social-action-count">{post.comment_count}</span>
        </Link>
        <ActionForm
          action={toggleSavePost}
          fields={{ post_id: post.id, remove: post.saved ? "yes" : "no" }}
        >
          <Submit aria-pressed={post.saved} aria-label={post.saved ? "Remove saved post" : "Save post"}>
            <SeraReactionIcon kind="save" active={post.saved} />
            <span>{post.saved ? "Saved" : "Save"}</span>
          </Submit>
        </ActionForm>
        <ShareButton id={post.id} title={post.title} count={post.share_count} />
      </footer>
      <details className="social-owner">
          <summary aria-label="Post options" title="Post options"><MoreHorizontal size={22} aria-hidden="true" /></summary>
          {owner && <Link href={"/posts/" + post.id + "/edit"}>
            <Pencil size={15} /> Edit story
          </Link>}
          {owner && <ActionForm
            action={deletePost}
            fields={{ post_id: post.id, return_to: detail ? "/feed" : "" }}
            confirm="Remove this post and all its comments?"
            reset
          >
            <Submit>
              <Trash2 size={15} /> Delete story
            </Submit>
          </ActionForm>}
          {!owner && <ActionForm action={reportPost} fields={{ post_id: post.id }} reset>
            <label htmlFor={"report-reason-" + post.id}>Report reason</label>
            <select id={"report-reason-" + post.id} name="reason" required defaultValue="">
              <option value="" disabled>Choose a reason</option>
              <option value="harassment">Harassment</option>
              <option value="hate">Hate speech</option>
              <option value="sexual">Sexual content</option>
              <option value="fraud">Fraud or scam</option>
              <option value="spam">Spam</option>
              <option value="violence">Violence</option>
              <option value="impersonation">Impersonation</option>
              <option value="privacy">Privacy violation</option>
              <option value="other">Other</option>
            </select>
            <Submit><Flag size={15} /> Submit report</Submit>
          </ActionForm>}
          {detail && owner && (
            <small>
              Deletion also removes this story from members’ saved posts.
            </small>
          )}
        </details>
    </article>
  );
}
