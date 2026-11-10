import Link from "next/link";
import "../profile-identity.css";
import { ProfessionalProfile } from "@/components/account/profile";
import { accountAssets } from "@/lib/account/assets";
import { requiredProfile } from "@/lib/auth/profile";
import { pageMetadata } from "@/lib/seo";
import { signOut } from "@/lib/auth/actions";
import { serverClient } from "@/lib/supabase/server";
import { saveProfileVisibility } from "@/lib/account/visibility";
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
  const db = await serverClient();
  const { data: visibilitySettings } = await db.from("professional_profile_visibility").select("visibility").eq("user_id", user.id).maybeSingle();
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
      <section className="account-security panel" aria-labelledby="profile-privacy-title">
        <h2 id="profile-privacy-title">Professional profile visibility</h2>
        <p>Choose your preferred audience. These preferences are saved privately. Public publishing and audience enforcement are not enabled yet; your member page currently requires sign-in.</p>
        <form action={saveProfileVisibility} className="sera-visibility-form">
          <label htmlFor="professional-visibility">Preferred audience</label>
          <select id="professional-visibility" name="visibility" defaultValue={visibilitySettings?.visibility || "members"}>
            <option value="members">SERA members</option>
            <option value="connections">Approved connections</option>
            <option value="public">Public internet</option>
          </select>
          <button className="button" type="submit">Save preference</button>
        </form>
      </section>
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
