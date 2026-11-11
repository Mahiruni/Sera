import { ProfileForm } from "@/components/auth/forms";
import { requiredProfile } from "@/lib/auth/profile";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/onboarding",
  "Make yourself at home",
  "Set up your private Sera.et profile and language preference.",
);
export default async function Page() {
  const { profile } = await requiredProfile();
  return (
    <div className="container page account-foundation">
      <h1>A little about you.</h1>
      <p className="subtext">
        Your email is verified. Add the details that help describe your next
        move. Optional fields can wait.
      </p>
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
