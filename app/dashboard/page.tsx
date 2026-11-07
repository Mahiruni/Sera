import Link from "next/link";
import { redirect } from "next/navigation";
import { requiredProfile } from "@/lib/auth/profile";
import { pageMetadata } from "@/lib/seo";
import { signOut } from "@/lib/auth/actions";
import { CheckCircle2, UserRound, BookOpen, ArrowUpRight } from "lucide-react";
export const metadata = pageMetadata(
  "/dashboard",
  "Your private dashboard",
  "Manage your private Sera.et account and preferences.",
);
export default async function Page() {
  const { user, profile } = await requiredProfile();
  if (profile && !profile.onboarded_at) redirect("/onboarding");
  const am = profile?.locale === "am";
  return (
    <div className="container page account-foundation" lang={am ? "am" : "en"}>
      <p className="eyebrow">Sera.et / {am ? "የእርስዎ ቦታ" : "YOUR SPACE"}</p>
      <h1>
        {am ? "እንኳን ደህና መጡ፣" : "Welcome,"} {profile?.full_name || "there"}.
      </h1>
      <p className="subtext">
        {am
          ? "ይህ የእርስዎ የግል መለያ ነው። የቅጥር እና የትምህርት መሣሪያዎች ገና ቀጥታ አይሠሩም።"
          : "Your private account is separate from the recruitment demo. Live job applications and academic tools are not launched."}
      </p>
      <div className="dashboard-status">
        <CheckCircle2 size={18} />
        <span>
          {am ? "የተረጋገጠ ኢሜይል" : "Email verified"} · <bdi>{user.email}</bdi>
        </span>
      </div>
      <div className="guide-grid">
        <Link className="panel" href="/settings">
          <UserRound size={23} />
          <h2>{am ? "መለያ እና ምርጫዎች" : "Account & preferences"}</h2>
          <p>
            {am
              ? "የግል መገለጫዎን እና ቋንቋዎን ያስተካክሉ።"
              : "Update your private details and email-language preference."}
          </p>
          <ArrowUpRight size={16} />
        </Link>
        <Link className="panel" href="/guides">
          <BookOpen size={23} />
          <h2>{am ? "ተግባራዊ መመሪያዎች" : "Practical guides"}</h2>
          <p>
            {am
              ? "ስለ ሥራ እና ትምህርት ጠቃሚ መመሪያዎችን ያንብቡ።"
              : "Read career and education planning resources."}
          </p>
          <ArrowUpRight size={16} />
        </Link>
        <Link className="panel" href="/demo">
          <h2>{am ? "የቅጥር ሙከራ" : "Recruitment demo"}</h2>
          <p>
            {am
              ? "የተለየ የሙከራ ይዘት። የመለያዎን መብቶች አይቀይርም።"
              : "Sample roles and browser-local workspaces. These do not change your real account permissions."}
          </p>
          <ArrowUpRight size={16} />
        </Link>
      </div>
      {!profile && (
        <p role="alert">
          Your profile could not be loaded. Please try again later.
        </p>
      )}
      <form action={signOut}>
        <button className="button secondary">{am ? "ውጣ" : "Sign out"}</button>
      </form>
    </div>
  );
}
