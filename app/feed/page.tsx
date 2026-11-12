import { requiredProfile } from "@/lib/auth/profile";
import { loadSocialFeed } from "@/lib/social/data";
import { loadNetworkSidebars } from "@/lib/network/data";
import { pageNumber, postKinds, type PostKind } from "@/lib/social/validation";
import { NetworkHome } from "@/components/network/home";
export const metadata = {
  title: "Your professional community",
  robots: { index: false, follow: false },
};
export default async function Feed({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { user, profile } = await requiredProfile("/feed");
  const params = await searchParams;
  const page = pageNumber(params.page),
    kind = postKinds.includes(params.kind as PostKind)
      ? (params.kind as PostKind)
      : undefined,
    mine = params.view === "mine";
  const personalized=!mine&&!kind&&params.view!=="latest";
  const [feed, sidebars] = await Promise.all([
    loadSocialFeed({ page, kind, authorId: mine ? user.id : undefined,personalized }),
    loadNetworkSidebars(user.id),
  ]);
  return (
    <NetworkHome
      userId={user.id}
      profile={profile}
      posts={feed.posts}
      {...sidebars}
      page={page}
      hasMore={feed.hasMore}
      kind={kind}
      mine={mine}
      personalized={personalized}
    />
  );
}
