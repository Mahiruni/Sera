"use client";
import { useState, useRef, useEffect } from "react";
import { useRouter } from "next/navigation";
import { MemberAvatar } from "@/components/activity/avatar";
import {
  Pencil,
  Camera,
  ShieldCheck,
  MapPin,
  FileText,
  Upload,
  Check,
  LockKeyhole,
  ArrowUpRight,
  X,
  BriefcaseBusiness,
  UserRound,
  Languages,
  GraduationCap,
  Sparkles,
  Share2,
  MoreHorizontal,
  Users,
  FolderKanban,
  BookOpen,
} from "lucide-react";
import type { Profile } from "@/components/auth/forms";
import type { Assets } from "@/lib/account/assets";
import { saveAccount } from "@/lib/account/actions";
import { locales } from "@/lib/locales";
function upload(
  data: FormData,
  progress: (n: number) => void,
): Promise<{ status?: string }> {
  return new Promise((resolve, reject) => {
    const x = new XMLHttpRequest();
    x.open("POST", "/api/account/media");
    x.upload.onprogress = (e) => {
      if (e.lengthComputable) progress(Math.round((e.loaded / e.total) * 100));
    };
    x.onerror = () =>
      reject(Error("Connection interrupted. Please try again."));
    x.onload = () => {
      try {
        const r = JSON.parse(x.responseText);
        if (x.status >= 400) reject(Error(r.error || "Upload failed."));
        else resolve(r);
      } catch {
        reject(Error("Upload failed. Please try again."));
      }
    };
    x.send(data);
  });
}
export function ProfessionalProfile({
  profile,
  email,
  assets,
  filesUnavailable = false,
}: {
  profile: Profile;
  email: string;
  assets: Assets | null;
  filesUnavailable?: boolean;
}) {
  const router = useRouter();
  const [activeSection, setActiveSection] = useState("overview");
  const [moreOpen, setMoreOpen] = useState(false);
  const [shareStatus, setShareStatus] = useState("");
  const [editing, setEditing] = useState(false),
    [saving, setSaving] = useState(false),
    [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false),
    [progress, setProgress] = useState(0),
    [source, setSource] = useState(""),
    [zoom, setZoom] = useState(1),
    [x, setX] = useState(0),
    [y, setY] = useState(0),
    [imageReady, setImageReady] = useState(false);
  const canvas = useRef<HTMLCanvasElement>(null),
    image = useRef<HTMLImageElement | null>(null),
    photoInput = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (!source) return;
    let current = true;
    const img = new Image();
    img.onload = () => {
      if (!current) return;
      if (img.naturalWidth * img.naturalHeight > 16000000) {
        setMessage("Choose an image below 16 megapixels.");
        setSource("");
        return;
      }
      image.current = img;
      setImageReady(true);
    };
    img.onerror = () => {
      if (current) {
        setMessage("This image could not be opened.");
        setSource("");
      }
    };
    img.src = source;
    return () => {
      current = false;
      URL.revokeObjectURL(source);
      image.current = null;
    };
  }, [source]);
  useEffect(() => {
    const img = image.current,
      c = canvas.current;
    if (!img || !c || !imageReady) return;
    const side = Math.min(img.naturalWidth, img.naturalHeight) / zoom;
    const ctx = c.getContext("2d");
    if (ctx) {
      ctx.fillStyle = "#fff";
      ctx.fillRect(0, 0, 512, 512);
      ctx.drawImage(
        img,
        ((img.naturalWidth - side) * (x + 1)) / 2,
        ((img.naturalHeight - side) * (y + 1)) / 2,
        side,
        side,
        0,
        0,
        512,
        512,
      );
    }
  }, [zoom, x, y, imageReady]);
  async function sendFile(
    kind: string,
    operation: string,
    file?: Blob,
    name?: string,
  ) {
    setBusy(true);
    setMessage("");
    setProgress(0);
    try {
      const f = new FormData();
      f.set("kind", kind);
      f.set("operation", operation);
      if (file) f.set("file", file, name || "profile.jpg");
      const r = await upload(f, setProgress);
      setMessage(
        r.status === "failed"
          ? "Original CV saved. Branded copy could not be created. Try another PDF export or retry below."
          : operation === "remove"
            ? "File removed."
            : "Your file is saved.",
      );
      setSource("");
      router.refresh();
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Please try again.");
    } finally {
      setBusy(false);
    }
  }
  async function savePhoto() {
    const preview = canvas.current;
    if (!preview || !imageReady || busy) return;
    // 512px square is enough for crisp 2x display on professional cards.
    // Prefer high JPEG quality; lower it only when the resulting file is large.
    const encode = (quality: number) =>
      new Promise<Blob | null>((resolve) => preview.toBlob(resolve, "image/jpeg", quality));
    setBusy(true);
    setMessage("Optimizing your profile photo…");
    try {
      let blob: Blob | null = null;
      for (const quality of [0.9, 0.85, 0.8, 0.75]) {
        blob = await encode(quality);
        if (!blob || blob.size <= 180 * 1024) break;
      }
      if (!blob) throw new Error("Could not process the image.");
      // sendFile manages upload progress and clears the busy state.
      await sendFile("avatar", "upload", blob, "profile.jpg");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not optimize photo.");
    } finally {
      setBusy(false);
    }
  }
  const detail = (
    label: string,
    value: string | undefined,
    placeholder: string,
  ) => (
    <div className="profile-detail" key={label}>
      <dt>{label}</dt>
      <dd>{value || <span className="profile-missing">{placeholder}</span>}</dd>
    </div>
  );
  return (
    <div className="professional-account">
      <div className="profile-topline">
        <p className="eyebrow">SERA / PROFESSIONAL PROFILE</p>
        <span>
          <LockKeyhole size={13} /> Private profile
        </span>
      </div>
      <section className="profile-hero-card">
        <div className="profile-cover">
          <span className="sera-cover-title">Your Professional<br/><strong>Journey</strong><br/>at <b>SERA.et</b></span>
          <span className="profile-cover-mark">Your next chapter starts here.</span>
        </div>
        <div className="profile-hero-body">
          <div className="profile-avatar-row">
            <div className="profile-avatar">
              <MemberAvatar
                name={profile.full_name}
                photo={
                  assets?.avatar_path
                    ? `/api/account/files?kind=avatar&v=${assets.version}`
                    : null
                }
                size={112}
              />
              <button
                className="profile-camera"
                aria-label="Upload or change profile photo"
                onClick={() => photoInput.current?.click()}
                disabled={busy || filesUnavailable}
              >
                <Camera size={17} />
              </button>
            </div>
            <div className="sera-profile-top-actions"><button
              className="button profile-edit"
              onClick={() => {
                setEditing((v) => !v);
                setMessage("");
              }}
              disabled={saving}
            >
              <Pencil size={15} />
              {editing ? "Close editor" : "Edit profile"}
            </button><button className="sera-profile-more" type="button" aria-label="Profile photo options" aria-expanded={moreOpen} onClick={()=>setMoreOpen(v=>!v)}><MoreHorizontal size={20}/></button></div>
          </div>
          <input
            className="sr-only"
            ref={photoInput}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            aria-label="Choose profile photo"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (!f) return;
              if (
                f.size > 8 * 1024 * 1024 ||
                !["image/jpeg", "image/png", "image/webp"].includes(f.type)
              ) {
                setMessage("Choose a JPG, PNG or WebP below 8 MB.");
                return;
              }
              setImageReady(false);
              setZoom(1);
              setX(0);
              setY(0);
              setSource(URL.createObjectURL(f));
              e.target.value = "";
            }}
          />
          {moreOpen && assets?.avatar_path && <button className="profile-text-button sera-profile-photo-remove" type="button" disabled={busy} onClick={()=>{if(window.confirm("Remove your profile photo?"))void sendFile("avatar","remove");}}>Remove profile photo</button>}
          <h1>{profile.full_name}</h1>
          <p className="profile-headline">
            {profile.headline ||
              "Add a professional headline to introduce yourself."}
          </p>
          <div className="profile-meta">
            <span>
              <MapPin size={14} />
              {profile.location || "Location not added"}
            </span>
            <span>
              <ShieldCheck size={14} /> Email confirmed
            </span>
            <span>
              {profile.journey === "employer" ? <BriefcaseBusiness size={14} /> : <UserRound size={14} />}
              {profile.journey === "employer"
                ? "Employer account"
                : "Career profile"}
            </span>
          </div>
          <div className="sera-profile-stat-row" aria-label="Profile summary">
            <div><Users size={19}/><strong>Professional network</strong><span>Grow your connections</span></div>
            <div><BookOpen size={19}/><strong>Career journey</strong><span>{profile.journey === "employer" ? "Employer" : "Professional"}</span></div>
            <div><FolderKanban size={19}/><strong>Portfolio</strong><span>Showcase your work</span></div>
          </div>
          <div className="sera-profile-quick-actions">
            <button type="button" className="button" onClick={()=>{setEditing(true);setActiveSection("experience");}}><BriefcaseBusiness size={17}/> Add experience</button>
            <a href="#cv" className="button secondary"><FileText size={17}/> {assets?.cv_path ? "Manage CV" : "Upload CV"}</a>
            <button type="button" className="button secondary" onClick={async()=>{try{await navigator.clipboard.writeText(window.location.href);setShareStatus("Profile link copied");}catch{setShareStatus("Unable to copy profile link");}}}><Share2 size={17}/> Share profile</button>
          </div>
          {shareStatus && <p role="status" className="profile-feedback">{shareStatus}</p>}
          <nav className="sera-profile-tabs" aria-label="Profile sections">
            {["overview","about","experience","skills","cv"].map(tab=><button type="button" key={tab} className={activeSection===tab?"is-active":""} aria-current={activeSection===tab?"page":undefined} onClick={()=>{setActiveSection(tab);document.getElementById(tab==="cv"?"cv":tab==="about"?"sera-profile-about":tab==="experience"||tab==="skills"?"sera-profile-background":"sera-profile-about")?.scrollIntoView({behavior:"smooth",block:"start"});}}>{tab==="cv"?"CV":tab.charAt(0).toUpperCase()+tab.slice(1)}</button>)}
          </nav>
        </div>
      </section>
      {message && (
        <p className="profile-feedback" role="status">
          {message}
        </p>
      )}
      {busy && (
        <div className="profile-upload-progress" role="status">
          <progress max={100} value={progress} />
          <span>
            {progress < 100
              ? `Uploading ${progress}%`
              : "Processing your file…"}
          </span>
        </div>
      )}
      {source && (
        <section
          className="profile-crop panel"
          aria-label="Adjust profile photo"
        >
          <div className="profile-section-heading">
            <h2>Make it yours</h2>
            <button
              className="profile-icon-button"
              aria-label="Cancel photo upload"
              disabled={busy}
              onClick={() => setSource("")}
            >
              <X size={19} />
            </button>
          </div>
          <canvas
            width={512}
            height={512}
            ref={canvas}
            aria-label="Profile photo crop preview"
          />
          <div className="profile-crop-sliders">
            {[
              ["Zoom", zoom, setZoom, 1, 3],
              ["Horizontal position", x, setX, -1, 1],
              ["Vertical position", y, setY, -1, 1],
            ].map(([label, value, set, min, max]) => (
              <label key={String(label)}>
                {String(label)}
                <input
                  type="range"
                  min={Number(min)}
                  max={Number(max)}
                  step="0.01"
                  value={Number(value)}
                  onChange={(e) =>
                    (set as (n: number) => void)(Number(e.target.value))
                  }
                  disabled={busy}
                />
              </label>
            ))}
          </div>
          <button
            className="button"
            onClick={savePhoto}
            disabled={busy || !imageReady}
          >
            <Check size={15} />
            Save photo
          </button>
        </section>
      )}
      {editing ? (
        <form
          className="profile-editor panel"
          onSubmit={async (e) => {
            e.preventDefault();
            const data = new FormData(e.currentTarget);
            setSaving(true);
            setMessage("");
            try {
              const result = await saveAccount(data);
              if (result.error) setMessage(result.error);
              else {
                setEditing(false);
                setMessage(result.warning || "Profile updated successfully.");
                router.refresh();
              }
            } catch {
              setMessage(
                "Could not connect. Your changes have not been saved.",
              );
            } finally {
              setSaving(false);
            }
          }}
        >
          <div className="profile-section-heading">
            <div>
              <p className="eyebrow">YOUR PROFESSIONAL STORY</p>
              <h2>Edit your profile</h2>
            </div>
          </div>
          <div className="profile-editor-grid">
            {[
              ["full_name", "Full name", profile.full_name, 100],
              ["headline", "Professional headline", profile.headline, 120],
              ["location", "Location", profile.location, 500],
              ["phone", "Phone (private)", profile.phone, 500],
              [
                "preferred_roles",
                "Preferred roles",
                profile.preferred_roles,
                500,
              ],
              ["company_name", "Organization", profile.company_name, 500],
              ["company_role", "Organization role", profile.company_role, 500],
            ].map(([name, label, value, max]) => (
              <label key={String(name)}>
                {label}
                <input
                  name={String(name)}
                  defaultValue={String(value || "")}
                  required={name === "full_name"}
                  minLength={name === "full_name" ? 2 : undefined}
                  maxLength={Number(max)}
                  type={name === "phone" ? "tel" : "text"}
                />
              </label>
            ))}
            <label>
              Account journey
              <select name="journey" defaultValue={profile.journey}>
                <option value="candidate">Career opportunities</option>
                <option value="employer">Employer</option>
              </select>
            </label>
            <label>
              Preferred language
              <select name="locale" defaultValue={profile.locale}>
                {locales.map((l) => (
                  <option key={l.code} value={l.code}>
                    {l.native}
                  </option>
                ))}
              </select>
            </label>
          </div>
          {[
            ["summary", "Professional summary", profile.summary, 2000],
            ["skills", "Skills", profile.skills, 500],
            ["experience", "Experience", profile.experience, 500],
            ["education", "Education", profile.education, 2000],
          ].map(([name, label, value, max]) => (
            <label key={String(name)}>
              {label}
              <textarea
                name={String(name)}
                defaultValue={String(value || "")}
                rows={4}
                maxLength={Number(max)}
              />
              <small>Up to {max} characters</small>
            </label>
          ))}
          <p className="profile-note">
            These details are private. Applying to a job shares only the details
            shown on that application. Choosing Employer does not verify your
            organization.
          </p>
          <div className="profile-actions">
            <button className="button" disabled={saving}>
              {saving ? "Saving…" : "Save changes"}
            </button>
            <button
              className="button secondary"
              type="button"
              disabled={saving}
              onClick={() => setEditing(false)}
            >
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <div className="profile-content-grid">
          <div className="profile-main-column">
            <section className="profile-section panel" id="sera-profile-about">
              <div className="profile-section-heading">
                <h2><Sparkles size={19} aria-hidden="true" /> About you</h2>
                <button
                  className="profile-icon-button"
                  aria-label="Edit professional summary"
                  onClick={() => setEditing(true)}
                >
                  <Pencil size={16} />
                </button>
              </div>
              <p
                className={
                  profile.summary ? "profile-prose" : "profile-missing"
                }
              >
                {profile.summary ||
                  "Tell your story: your strengths, interests, and what you would like to do next."}
              </p>
            </section>
            <section className="profile-section panel" id="sera-profile-background">
              <h2><GraduationCap size={19} aria-hidden="true" /> Professional background</h2>
              <dl>
                {detail(
                  "Skills",
                  profile.skills,
                  "Add the skills you bring to your work.",
                )}
                {detail(
                  "Experience",
                  profile.experience,
                  "Share a concise overview of your experience.",
                )}
                {detail(
                  "Education",
                  profile.education,
                  "Add your education and qualifications.",
                )}
              </dl>
            </section>
          </div>
          <aside className="profile-side-column">
            <section className="profile-section panel">
              <h2><Languages size={19} aria-hidden="true" /> Personal details</h2>
              <dl>
                {detail("Email", email, "")}
                {detail("Phone", profile.phone, "Not added")}
                {detail(
                  "Preferred roles",
                  profile.preferred_roles,
                  "Not added",
                )}
                {detail(
                  "Language",
                  locales.find((l) => l.code === profile.locale)?.native,
                  "English",
                )}
                {profile.journey === "employer" &&
                  detail("Organization", profile.company_name, "Not added")}
                {profile.journey === "employer" &&
                  detail("Your role", profile.company_role, "Not added")}
              </dl>
            </section>
            <div className="profile-private-note">
              <LockKeyhole size={20} />
              <div>
                <strong>Your information stays yours.</strong>
                <p>
                  Your photo and CV are private. Uploading a CV does not send it
                  to employers.
                </p>
              </div>
            </div>
          </aside>
        </div>
      )}
      <section className="profile-section profile-cv panel" id="cv">
        <div className="profile-section-heading">
          <div>
            <p className="eyebrow">READY FOR YOUR NEXT OPPORTUNITY</p>
            <h2>Your CV</h2>
          </div>
          <FileText size={26} />
        </div>
        {filesUnavailable ? (
          <p role="alert">
            Account files are temporarily unavailable. Refresh to try again.
          </p>
        ) : assets?.cv_path ? (
          <>
            <div className="profile-cv-file">
              <span className="profile-file-icon">
                <FileText size={23} />
              </span>
              <div>
                <strong>{assets.cv_name}</strong>
                <p>
                  Uploaded{" "}
                  {assets.cv_uploaded_at
                    ? new Date(assets.cv_uploaded_at).toLocaleDateString("en", {
                        year: "numeric",
                        month: "short",
                        day: "numeric",
                      })
                    : ""}{" "}
                  · Private
                </p>
              </div>
              <span
                className={`profile-file-status ${assets.cv_status === "ready" ? "ready" : ""}`}
              >
                {assets.cv_status === "ready"
                  ? "Branded copy ready"
                  : "Original saved"}
              </span>
            </div>
            <div className="profile-download-grid">
              {[
                ["cv", "Original CV"],
                ...(assets.branded_path
                  ? [["branded", "Sera-branded CV"]]
                  : []),
              ].map(([kind, label]) => (
                <div key={kind}>
                  <h3>{label}</h3>
                  <a
                    href={`/api/account/files?kind=${kind}`}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Preview <ArrowUpRight size={13} />
                  </a>
                  <a href={`/api/account/files?kind=${kind}&download=1`}>
                    Download
                  </a>
                </div>
              ))}
            </div>
            {assets.cv_status === "failed" && (
              <p className="profile-note">
                The original is safe. Branding supports unencrypted PDFs up to
                40 pages. Try another PDF export, or{" "}
                <button
                  className="profile-text-button"
                  onClick={() => sendFile("cv", "retry")}
                  disabled={busy}
                >
                  retry processing
                </button>
                .
              </p>
            )}
            <button
              className="profile-text-button"
              disabled={busy}
              onClick={() => {
                if (
                  window.confirm(
                    "Delete your original CV and its branded copy?",
                  )
                )
                  void sendFile("cv", "remove");
              }}
            >
              Delete CV and branded copy
            </button>
          </>
        ) : (
          <div className="profile-cv-empty">
            <Upload size={27} />
            <h3>A stronger introduction starts here.</h3>
            <p>
              Keep your CV ready in one private place. We create a separate copy
              with a discreet Sera footer.
            </p>
          </div>
        )}
        {!filesUnavailable && (
          <label className={`profile-file-picker ${busy ? "is-busy" : ""}`}>
            <Upload size={16} />
            {assets?.cv_path ? "Replace CV" : "Upload your CV"}
            <input
              type="file"
              accept="application/pdf,.pdf"
              disabled={busy}
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (!f) return;
                if (f.size > 3 * 1024 * 1024 || f.type !== "application/pdf") {
                  setMessage("Choose a PDF no larger than 3 MB.");
                  e.target.value = "";
                  return;
                }
                void sendFile("cv", "upload", f, f.name);
                e.target.value = "";
              }}
            />
          </label>
        )}
        <p className="profile-note">
          PDF · Maximum 3 MB. The original stays unchanged. The branded copy
          adds a footer below each page; interactive fields and digital
          signatures are not retained in that separate copy. No CV is shared
          automatically.
        </p>
      </section>
    </div>
  );
}
