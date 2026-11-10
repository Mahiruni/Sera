import { notFound } from "next/navigation";
import { requiredProfile } from "@/lib/auth/profile";
import { loadSocialFeed } from "@/lib/social/data";
import { uuid } from "@/lib/social/validation";
import { PostComposer } from "@/components/social/PostComposer";
import { SocialNav } from "@/components/social/feed";
export const metadata = {
  title: "Edit your story",
  robots: { index: false, follow: false },
};
export default async function Edit({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { user } = await requiredProfile();
  const { id } = await params;
  if (!uuid(id)) notFound();
  const { posts } = await loadSocialFeed({ postId: id, authorId: user.id });
  if (!posts[0]) notFound();
  return (
    <div className="container page social-page social-detail">
      <SocialNav />
      <PostComposer post={posts[0]} />
    </div>
  );
}
