import Link from "next/link";
import { ProfileForm } from "@/components/auth/forms";
import { requiredProfile } from "@/lib/auth/profile";
import { pageMetadata } from "@/lib/seo";
import { signOut } from "@/lib/auth/actions";
export const metadata = pageMetadata(
  "/settings",
  "Account settings",
  "Manage your private profile, language and account session.",
);
export default async function Page() {
  const { profile, user } = await requiredProfile();
  return (
    <div className="container page account-foundation">
      <p className="eyebrow">Sera.et / ACCOUNT</p>
      <h1>Your details. Your preferences.</h1>
      <p className="subtext">
        Signed in as <bdi>{user.email}</bdi>. Profile edits apply to your own
        account.
      </p>
      {profile ? (
        <ProfileForm profile={profile} />
      ) : (
        <p role="alert">Profile unavailable. Please try again later.</p>
      )}
      <div className="account-security panel">
        <h2>Account security</h2>
        <p>
          Use a unique password. A password-reset email confirms access to your
          registered address before you choose a replacement.
        </p>
        <Link className="inline-link" href="/forgot-password">
          Request a password reset
        </Link>
        <form action={signOut}>
          <button className="button secondary">Sign out</button>
        </form>
      </div>
    </div>
  );
}
