import { MemberAvatar } from "@/components/activity/avatar";
import Link from "next/link";
import { addComment, deleteComment,editComment,likeComment } from "@/lib/social/actions";
import type { SocialComment } from "@/lib/social/data";
import { ActionForm, Submit } from "./forms";
import { SeraReactionIcon } from "./SeraReactionIcon";
function CommentForm({
  postId,
  parentId,
}: {
  postId: string;
  parentId?: string;
}) {
  return (
    <ActionForm
      action={addComment}
      fields={{ post_id: postId, ...(parentId ? { parent_id: parentId } : {}) }}
      reset
    >
      <label className="sera-comment-field">
        {parentId ? "Your reply" : "Join the conversation"}
        <textarea
          name="body"
          required
          maxLength={2000}
          rows={2}
          placeholder={
            parentId ? "Write a thoughtful reply…" : "Add your perspective…"
          }
        />
      </label>
      <Submit className="button sera-comment-submit">
        <SeraReactionIcon kind="comment" />
        {parentId ? "Reply" : "Post comment"}
      </Submit>
    </ActionForm>
  );
}
export function Comments({
  comments,
  postId,
  userId,
  ownerId,
  page,
  hasMore,
}: {
  comments: SocialComment[];
  postId: string;
  userId: string;
  ownerId: string;
  page: number;
  hasMore: boolean;
}) {
  const render = (c: SocialComment) => (
    <div className="social-comment" key={c.id} id={"comment-" + c.id}>
      <Link className="social-author" href={"/members/" + c.author_id}>
        <MemberAvatar name={c.author} photo={c.photo} size={28} /> {c.author}
      </Link>
      {c.headline&&<small className="sera-author-occupation">{c.headline}</small>}
      <p>{c.body}</p>
      <ActionForm action={likeComment} fields={{post_id:postId,comment_id:c.id,liked:c.liked?"no":"yes"}}><Submit aria-pressed={!!c.liked}><SeraReactionIcon kind="like" active={!!c.liked}/>{c.like_count||0}</Submit></ActionForm>
      {c.author_id===userId&&<details><summary>Edit comment</summary><ActionForm action={editComment} fields={{post_id:postId,comment_id:c.id}}><textarea name="body" maxLength={2000} required defaultValue={c.body}/><Submit>Save changes</Submit></ActionForm></details>}
      <small>
        <time dateTime={c.created_at}>
          {new Date(c.created_at).toLocaleDateString("en-GB", {
            timeZone: "Africa/Addis_Ababa",
          })}
        </time>
      </small>
      {(c.author_id === userId || ownerId === userId) && (
        <ActionForm
          action={deleteComment}
          fields={{ comment_id: c.id, post_id: postId }}
          confirm="Remove this comment and its replies?"
        >
          <Submit className="social-remove-comment">Remove</Submit>
        </ActionForm>
      )}
    </div>
  );
  return (
    <section className="social-comments" id="comments">
      <div className="sera-conversation-heading"><SeraReactionIcon kind="comment" /><h2>The conversation</h2></div>
      <CommentForm postId={postId} />
      {!comments.length && <p>No comments yet. Start the conversation.</p>}
      {comments
        .filter((c) => !c.parent_id)
        .map((c) => (
          <div className="social-thread" key={c.id} id={"comment-" + c.id}>
            {render(c)}
            <div className="social-replies">
              {comments.filter((r) => r.parent_id === c.id).map(render)}
            </div>
            <details>
              <summary>Reply to {c.author}</summary>
              <CommentForm postId={postId} parentId={c.id} />
            </details>
          </div>
        ))}
      <nav className="social-pagination" aria-label="Comment pages">
        {page > 1 && (
          <Link href={`?comments=${page - 1}#comments`}>← Newer comments</Link>
        )}
        {hasMore && (
          <Link href={`?comments=${page + 1}#comments`}>Older comments →</Link>
        )}
      </nav>
    </section>
  );
}
