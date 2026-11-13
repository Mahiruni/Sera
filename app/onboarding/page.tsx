import { PageIntro } from "@/components/page-intro";
import { UserRound } from "lucide-react";
import { ProfileForm } from "@/components/auth/forms";
import { requiredProfile } from "@/lib/auth/profile";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/onboarding",
  "Make yourself at home",
  "Set up your private Sera profile and language preference.",
);
export default async function Page() {
  const { profile } = await requiredProfile();
  return (
    <div className="container page account-foundation sera-web-onboarding">
      <PageIntro icon={UserRound} title="Set up your profile" description="Your email is verified. Add your professional details now, or complete optional fields later."/>
      {profile ? (
        <ProfileForm profile={profile} />
      ) : (
        <p role="alert">
          Your account is verified, but its profile is unavailable. Please try
          again later.
        </p>
      )}
    </div>
  );
}
