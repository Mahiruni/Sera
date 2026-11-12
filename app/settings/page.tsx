import Link from "next/link";
import {canReviewIdentity} from "@/lib/identity/access";
import {PortfolioEditor} from "@/components/account/portfolio-editor";
import {UsernameEditor} from "@/components/account/username-editor";
import "../profile-identity.css";
import { ProfessionalProfile } from "@/components/account/profile";
import { accountAssets } from "@/lib/account/assets";
import { requiredProfile } from "@/lib/auth/profile";
import { pageMetadata } from "@/lib/seo";
import { signOut } from "@/lib/auth/actions";
import { serverClient } from "@/lib/supabase/server";
import { saveProfileVisibility } from "@/lib/account/visibility";
import { updatePublicPortfolio } from "@/lib/account/public-portfolio";
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
  const { data: publicPortfolio } = await db.from("professional_public_portfolios").select("published").eq("user_id",user.id).maybeSingle();
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
      <section className="account-security panel"><h2>Identity verification</h2><p>Request a verified professional identity badge.</p><Link className="button secondary" href="/settings/verification">Verify your identity</Link>{canReviewIdentity(user)&&<Link className="button secondary" href="/admin/verification">Review identity requests</Link>}</section><section className="account-security panel"><h2>Content management</h2><p>Manage deleted posts and restore them within 30 days.</p><Link className="button secondary" href="/settings/bin">Open bin</Link></section>
      <UsernameEditor />
      <PortfolioEditor userId={user.id}/>
      <section className="account-security panel" aria-labelledby="profile-privacy-title">
        <h2 id="profile-privacy-title">Professional profile visibility</h2>
        <p>Choose who can open your professional member profile. Connections-only access is checked on the member page; public internet access requires separately publishing your portfolio below. Other community surfaces are still being reviewed.</p>
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
      <section className="account-security panel" aria-labelledby="portfolio-publishing-title">
        <h2 id="portfolio-publishing-title">Public portfolio publishing</h2>
        <p>Publishing is optional. Your professional name, headline, summary, location, skills, experience and education become visible on a shareable page. Your email, phone, CV and private activity are excluded. Changes to your professional information require republishing to update this public copy.</p>
        <p><strong>Status:</strong> {publicPortfolio?.published ? "Published" : "Not published"}</p>
        <div className="sera-visibility-form">
          <form action={updatePublicPortfolio}><input type="hidden" name="intent" value={publicPortfolio?.published ? "unpublish" : "publish"} /><button className="button" type="submit" disabled={!publicPortfolio?.published && visibilitySettings?.visibility !== "public"}>{publicPortfolio?.published ? "Unpublish portfolio" : "Publish portfolio"}</button></form>
          {publicPortfolio?.published && <Link className="inline-link" href={`/portfolio/${user.id}`}>View public portfolio</Link>}
        </div>
        {visibilitySettings?.visibility !== "public" && !publicPortfolio?.published && <p>Choose Public internet above and save your preference before publishing.</p>}
      </section>
      <section className="account-security panel" aria-labelledby="account-safety-title">
        <h2 id="account-safety-title">Community safety</h2>
        <p>Review the status of posts you have reported. Your reports are private.</p>
        <Link className="inline-link" href="/settings/safety">View your safety reports</Link>
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
