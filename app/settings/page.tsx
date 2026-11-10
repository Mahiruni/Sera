import Link from "next/link";
import "../profile-identity.css";
import { ProfessionalProfile } from "@/components/account/profile";
import { accountAssets } from "@/lib/account/assets";
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
  let assets = null;
  let filesUnavailable = false;
  try {
    assets = await accountAssets(user.id);
  } catch {
    filesUnavailable = true;
  }
  return (
    <div className="container page account-foundation">
      {profile ? (
        <ProfessionalProfile
          profile={profile}
          email={user.email || ""}
          assets={assets}
          filesUnavailable={filesUnavailable}
        />
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
