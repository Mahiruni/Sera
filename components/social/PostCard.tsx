import Link from "next/link";
import { toggleLikePost, toggleSavePost, deletePost } from "@/lib/social/actions";

export type Post = {
  id: string;
  user_id: string;
  type: 'text' | 'image' | 'quote' | 'talent' | 'project' | 'achievement' | 'discussion';
  title?: string | null;
  content: string;
  image_url?: string | null;
  skills?: string[];
  category?: string | null;
  location?: string | null;
  created_at: string;
  profiles?: { full_name?: string; headline?: string; avatar_url?: string } | null;
  likes_count?: number;
  comments_count?: number;
  is_liked?: boolean;
  is_saved?: boolean;
};

export function PostCard({ post, currentUserId }: { post: Post; currentUserId?: string }) {
  const isOwner = currentUserId === post.user_id;

  return (
    <article className="sera-post-card">
      <div className="post-header">
        <div className="post-author-info">
          <div className="author-avatar">
            {post.profiles?.avatar_url ? (
              <img src={post.profiles.avatar_url} alt="" />
            ) : (
              <span>{(post.profiles?.full_name || "U")[0]}</span>
            )}
          </div>
          <div>
            <h4>{post.profiles?.full_name || "Professional Member"}</h4>
            <p>{post.profiles?.headline || post.category || "SERA.et Community"}</p>
          </div>
        </div>
        <div className="post-meta-badge">
          <span className={`badge-type ${post.type}`}>{post.type}</span>
          {post.location && <span className="post-location">📍 {post.location}</span>}
        </div>
      </div>

      {post.title && <h3 className="post-title"><Link href={`/posts/${post.id}`}>{post.title}</Link></h3>}

      <div className="post-body">
        {post.type === 'quote' ? (
          <blockquote className="editorial-quote">
            "{post.content}"
          </blockquote>
        ) : (
          <p>{post.content}</p>
        )}

        {post.image_url && (
          <div className="post-image-container">
            <img src={post.image_url} alt={post.title || "Post media"} loading="lazy" />
          </div>
        )}

        {post.skills && post.skills.length > 0 && (
          <div className="post-skills">
            {post.skills.map((skill) => (
              <span key={skill} className="skill-tag">{skill}</span>
            ))}
          </div>
        )}
      </div>

      <div className="post-footer">
        <div className="post-actions-left">
          <form action={toggleLikePost}>
            <input type="hidden" name="post_id" value={post.id} />
            <input type="hidden" name="is_liked" value={post.is_liked ? "true" : "false"} />
            <button className={`action-btn ${post.is_liked ? "active" : ""}`} type="submit">
              ❤️ {post.likes_count || 0}
            </button>
          </form>

          <Link href={`/posts/${post.id}`} className="action-btn">
            💬 {post.comments_count || 0}
          </Link>

          <form action={toggleSavePost}>
            <input type="hidden" name="post_id" value={post.id} />
            <input type="hidden" name="is_saved" value={post.is_saved ? "true" : "false"} />
            <button className={`action-btn ${post.is_saved ? "active" : ""}`} type="submit">
              🔖 {post.is_saved ? "Saved" : "Save"}
            </button>
          </form>
        </div>

        {isOwner && (
          <div className="post-owner-actions">
            <form action={deletePost} onSubmit={(e) => { if (!confirm("Delete this post?")) e.preventDefault(); }}>
              <input type="hidden" name="post_id" value={post.id} />
              <button className="delete-btn" type="submit">Delete</button>
            </form>
          </div>
        )}
      </div>
    </article>
  );
}
