import { PostOptions } from "./PostOptions";
import { MemberAvatar } from "@/components/activity/avatar";
import Link from "next/link";
import {IdentityBadge} from "@/components/identity/badge";
import { ArrowUpRight } from "lucide-react";
import { toggleLikePost, toggleSavePost } from "@/lib/social/actions";
import type { SocialPost } from "@/lib/social/data";
import { ActionForm, Submit } from "./forms";
import { ShareButton } from "./share";
import { RepostButton } from "./RepostButton";
import { SeraConversationSheet } from "./SeraConversationSheet";
import { SeraReactionIcon } from "./SeraReactionIcon";
import { ExpandableCaption, PostMedia } from "./post-media";
function relativePostTime(value:string){
  const date=new Date(value), delta=Math.max(0,Math.floor((Date.now()-date.getTime())/60000));
  if(!Number.isFinite(delta))return "";
  if(delta<1)return "Just now";
  if(delta<60)return delta+"m ago";
  if(delta<1440)return Math.floor(delta/60)+"h ago";
  if(delta<10080)return Math.floor(delta/1440)+"d ago";
  return date.toLocaleDateString("en-GB",{timeZone:"Africa/Addis_Ababa",day:"numeric",month:"short",...(date.getFullYear()!==new Date().getFullYear()?{year:"numeric" as const}:{})});
}
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
  const title = (post.repost_of?post.original?.title:post.title)?.trim() || "";
  const body = (post.repost_of?post.original?.body:post.body)?.trim() || "";
  // Legacy posts sometimes repeat the title verbatim at the beginning of the body.
  // Keep the full original body; suppress only a redundant title presentation.
  const showTitle = Boolean(title) && !(normalize(body) === normalize(title) || normalize(body).startsWith(normalize(title) + " "));
  return (
    <article className={`social-card social-card-${post.kind}`}>
      <header className="social-post-header">
        <Link
          className="social-avatar"
          prefetch={false}
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
          <Link prefetch={false} href={"/members/" + post.author_id} className="social-author">
            {post.author.full_name}{post.identityVerified&&<IdentityBadge/>}
          </Link>{post.repost_of&&<span className="sera-repost-byline"> | Reposted from {post.original?<Link prefetch={false} href={"/members/"+post.original.author_id}>{post.original.name}</Link>:"original author"}</span>}
          {post.author.headline&&<p className="sera-author-occupation">{post.author.headline}</p>}
          <small>
            <time dateTime={post.published_at||post.created_at} title={new Date(post.published_at||post.created_at).toLocaleString("en-GB",{timeZone:"Africa/Addis_Ababa"})}>
              {post.repost_of?"Reposted ":""}{relativePostTime(post.published_at||post.created_at)}
            </time>
            {post.edited_at ? " · Edited" : ""}
          </small>
        </div>
        <PostOptions post={post} userId={currentUserId} detail={detail}/>
      </header>
      {post.repost_of&&<>{post.repost_note&&<ExpandableCaption text={post.repost_note}/>}<div className="sera-original-byline">{post.original?<><MemberAvatar name={post.original.name} photo={post.original.photo} size={30}/><div><Link prefetch={false} href={"/posts/"+post.original.id}>{post.original.name}</Link><small>Original · {new Date(post.original.published_at).toLocaleString("en-GB",{timeZone:"Africa/Addis_Ababa",day:"numeric",month:"short",hour:"2-digit",minute:"2-digit"})}</small></div></>:<span>Original post unavailable</span>}</div></>}
      {showTitle && (
        <h2>
          <Link prefetch={false} href={"/posts/" + post.id}>{title}</Link>
        </h2>
      )}
      {post.kind === "quote" ? (
        <blockquote>
          <p>{post.body}</p>
          {post.quote_source && <cite>— {post.quote_source}</cite>}
        </blockquote>
      ) : (
        <ExpandableCaption text={body} />
      )}
      <PostMedia images={post.repost_of?post.original?.images||[]:post.images} />
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
      <footer className="social-post-actions sera-premium-reactions" aria-label="Post interactions">
        <ActionForm
          action={toggleLikePost}
          fields={{ post_id: post.id, remove: post.liked ? "yes" : "no" }}
        >
          <Submit preserveContent
            aria-pressed={post.liked}
            aria-label={`${post.liked ? "Unlike" : "Like"} post, ${post.like_count} likes`}
          >
            <SeraReactionIcon kind="like" active={post.liked} />
            <span className="social-action-count">{post.like_count}</span>
          </Submit>
        </ActionForm>
        <SeraConversationSheet postId={post.id} count={post.comment_count} author={post.author.full_name} currentUserId={currentUserId} />
        <RepostButton id={post.repost_of||post.id} author={post.original?.name||post.author.full_name} reposted={post.reposted||false} count={post.repost_count||0} disabled={post.repost_of?(!post.original||post.original.author_id===currentUserId||post.original.audience!=="public"):(owner||post.audience!==undefined&&post.audience!=="public")} />
        <ShareButton id={post.id} title={post.title} count={post.share_count} />
        <ActionForm action={toggleSavePost} fields={{post_id:post.id,remove:post.saved?"yes":"no"}}><Submit preserveContent aria-pressed={post.saved} aria-label={post.saved?"Remove saved post":"Save post"}><SeraReactionIcon kind="save" active={post.saved}/></Submit></ActionForm>
      </footer>

    </article>
  );
}
