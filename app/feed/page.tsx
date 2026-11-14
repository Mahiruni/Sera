import {Suspense} from "react";
import {memberCard} from "@/lib/activity/members";
import { requiredProfile } from "@/lib/auth/profile";
import { loadSocialFeed } from "@/lib/social/data";
import { loadNetworkSidebars } from "@/lib/network/data";
import { pageNumber, postKinds, type PostKind } from "@/lib/social/validation";
import { NetworkHome, NetworkPeopleStrip, NetworkRightRail } from "@/components/network/home";
import {PostList,SocialPagination} from "@/components/social/feed";
import {CachedFeedPreview,SaveFeedPreview} from "@/components/social/cached-feed-preview";
import {publicFeedPreviews} from "@/lib/social/public-feed-cache";

export const metadata={title:"Your professional community",robots:{index:false,follow:false}};
type FeedPromise=ReturnType<typeof loadSocialFeed>;
type SidebarPromise=ReturnType<typeof loadNetworkSidebars>;

async function FeedResults({result,userId,page,href,cacheable}:{result:FeedPromise;userId:string;page:number;href:string;cacheable:boolean}){
  const feed=await result.catch(()=>null);
  if(!feed&&cacheable)return <CachedFeedPreview owner={userId} failed href={href}/>;
  if(!feed)return <div className="social-empty" role="alert"><h2>Unable to load your feed</h2><p>Check your connection, then refresh to try again.</p><a href={href}>Retry</a></div>;
  return <>{cacheable&&<SaveFeedPreview owner={userId} rows={publicFeedPreviews(feed.posts)}/>}<PostList posts={feed.posts} userId={userId}/><SocialPagination page={page} hasMore={feed.hasMore} href={href}/></>;
}
async function People({result}:{result:SidebarPromise}){
  const data=await result.catch(()=>null);
  return <NetworkPeopleStrip people={data?.people||[]}/>;
}
async function RightRail({result}:{result:SidebarPromise}){
  const data=await result.catch(()=>null);
  return data?<NetworkRightRail {...data}/>:<NetworkRightRail people={[]} jobs={[]} peopleError jobsError/>;
}
function FeedSkeleton(){
  return <div aria-label="Loading posts" aria-busy="true" className="social-post-list sera-feed-loading">
    {[0,1,2].map(i=><div key={i} className="social-card"><div className="sera-feed-skeleton-head"><span/><span/></div><span className="sera-feed-skeleton-line"/><span className="sera-feed-skeleton-line short"/></div>)}
  </div>;
}
export default async function Feed({searchParams}:{searchParams:Promise<Record<string,string|string[]|undefined>>}){
  const [{user,profile},params]=await Promise.all([requiredProfile("/feed"),searchParams]);
  const page=pageNumber(params.page),kind=postKinds.includes(params.kind as PostKind)?params.kind as PostKind:undefined,mine=params.view==="mine";
  const personalized=!mine&&!kind&&params.view==="for-you";
  const feed=loadSocialFeed({page,kind,authorId:mine?user.id:undefined,personalized});
  const sidebars=loadNetworkSidebars(user.id);
  const member=await memberCard(user.id);
  const filters=new URLSearchParams();
  if(kind)filters.set("kind",kind);
  if(mine)filters.set("view","mine");
  else if(personalized)filters.set("view","for-you");
  const href="/feed"+(filters.size?"?"+filters.toString():"");
  const cacheable=page===1&&!kind&&!mine&&!personalized;
  return <NetworkHome userId={user.id} profile={profile} posts={[]} people={[]} jobs={[]} avatarUrl={member?.photo||null}
    page={page} kind={kind} mine={mine} personalized={personalized} identityVerified={member?.identityVerified===true}
    peopleContent={<Suspense fallback={<NetworkPeopleStrip people={[]}/>}><People result={sidebars}/></Suspense>}
    feedContent={<Suspense fallback={cacheable?<CachedFeedPreview owner={user.id}/>:<FeedSkeleton/>}><FeedResults result={feed} userId={user.id} page={page} href={href} cacheable={cacheable}/></Suspense>}
    rightContent={<Suspense fallback={<aside className="network-right" aria-busy="true"/>}><RightRail result={sidebars}/></Suspense>}/>;
}
