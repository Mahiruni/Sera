import { additionalEmailCopy } from "./translations";
export const supportedEmailLocales = [
  "en",
  "am",
  "om",
  "ti",
  "ar",
  "so",
] as const;
export type EmailKind =
  "confirmation" | "recovery" | "welcome" | "password_changed";
export const emailKinds: EmailKind[] = [
  "confirmation",
  "recovery",
  "welcome",
  "password_changed",
];
export function escapeHtml(value: string) {
  return value.replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ]!,
  );
}
const copy = {
  en: {
    hello: "Hello",
    greeting: "A warm welcome to your next chapter.",
    eyebrow: "A LITTLE CLARITY. A NEW POSSIBILITY.",
    intro:
      "Thank you for choosing Sera. We are building a thoughtful place to explore opportunities, plan learning and take your next step with confidence.",
    confirmation: {
      subject: "Confirm your Sera email address",
      heading: "Your next chapter starts here.",
      body: "Please confirm your email address to finish setting up your personal account. This step helps us make sure your space belongs to you.",
      action: "Confirm my email",
    },
    recovery: {
      subject: "Reset your Sera password",
      heading: "Let’s get you back to your space.",
      body: "We received a request to reset your password. Use the secure link below to choose a new password. If you did not request this, you can ignore this email.",
      action: "Choose a new password",
    },
    welcome: {
      subject: "Welcome to Sera — make yourself at home",
      heading: "A warm welcome, and a clearer direction.",
      body: "Your email is verified. Start with a few details about yourself, choose your preferred language, and explore the practical resources available today.",
      action: "Set up my profile",
    },
    password_changed: {
      subject: "Your Sera password was changed",
      heading: "A quick account-security update.",
      body: "Your account password was changed. If this was you, no action is needed. If you did not make this change, request a password reset from the official Sera website.",
      action: "Review account security",
    },
    first: "Start with what matters to you",
    firstBody:
      "Keep your profile details and language preference in one private place. Optional information can wait until it is useful.",
    second: "Explore at your own pace",
    secondBody:
      "Explore employer-posted jobs and practical career guides. Check each opportunity carefully before applying.",
    security: "A small reminder for a safer journey",
    securityBody:
      "Never share passwords, verification links or codes. Sera will not ask for your password by email. Do not enter financial, identity or private student records into the demo.",
    fallback: "If the button does not open, copy this link into your browser:",
    help: "Need a hand?",
    helpBody:
      "For account help, contact mahir@hisabtech.com. Never send your password or verification links.",
    footer: "Opportunities · Education · A clearer next move",
    reason:
      "You received this transactional email because of an account action on Sera. It is not a marketing subscription.",
    helpLink: "Help & account information",
    privacy: "Privacy information",
  },
  am: {
    hello: "ሰላም",
    greeting: "ወደ ቀጣዩ ምዕራፍዎ እንኳን በደህና መጡ።",
    eyebrow: "ግልጽ መንገድ። አዲስ ዕድል።",
    intro:
      "Seraን ስለመረጡ እናመሰግናለን። ዕድሎችን ለማሰስ፣ ትምህርትን ለማቀድ እና ቀጣዩን እርምጃ በእርግጠኝነት ለመውሰድ የሚያግዝ ቦታ እየገነባን ነው።",
    confirmation: {
      subject: "የSera ኢሜይል አድራሻዎን ያረጋግጡ",
      heading: "ቀጣዩ ምዕራፍዎ እዚህ ይጀምራል።",
      body: "የግል መለያዎን ማዘጋጀት ለማጠናቀቅ እባክዎ ኢሜይልዎን ያረጋግጡ። ይህ እርምጃ መለያው የእርስዎ መሆኑን ለማረጋገጥ ይረዳናል።",
      action: "ኢሜይሌን አረጋግጥ",
    },
    recovery: {
      subject: "የSera የይለፍ ቃልዎን ያድሱ",
      heading: "ወደ የእርስዎ ቦታ እንመልስዎ።",
      body: "የይለፍ ቃልዎን ለማደስ ጥያቄ ደርሶናል። አዲስ የይለፍ ቃል ለመምረጥ ከታች ያለውን አገናኝ ይጠቀሙ። ይህን ካልጠየቁ ኢሜይሉን ችላ ማለት ይችላሉ።",
      action: "አዲስ የይለፍ ቃል ምረጥ",
    },
    welcome: {
      subject: "እንኳን ወደ Sera በደህና መጡ",
      heading: "ሞቅ ያለ አቀባበል፣ ግልጽ መንገድ።",
      body: "ኢሜይልዎ ተረጋግጧል። ስለ እርስዎ ጥቂት መረጃ ያክሉ፣ የሚመርጡትን ቋንቋ ይምረጡ እና ያሉትን ተግባራዊ መመሪያዎች ያስሱ።",
      action: "መገለጫዬን አዘጋጅ",
    },
    password_changed: {
      subject: "የSera የይለፍ ቃልዎ ተቀይሯል",
      heading: "ስለ መለያዎ ደህንነት አጭር ማሳወቂያ።",
      body: "የመለያዎ የይለፍ ቃል ተቀይሯል። ይህን እርስዎ ካደረጉት ሌላ እርምጃ አያስፈልግም። እርስዎ ካልቀየሩት ከኦፊሴላዊው የSera ድረ ገጽ የይለፍ ቃል ማደስ ይጠይቁ።",
      action: "የመለያ ደህንነት ይመልከቱ",
    },
    first: "ለእርስዎ አስፈላጊ ከሆነው ይጀምሩ",
    firstBody:
      "መገለጫዎን እና የቋንቋ ምርጫዎን በአንድ የግል ቦታ ያስቀምጡ። አማራጭ መረጃ እስኪያስፈልግ ድረስ መጠበቅ ይችላል።",
    second: "በራስዎ ፍጥነት ያስሱ",
    secondBody:
      "በቀጣሪዎች የተለጠፉ ሥራዎችን እና ተግባራዊ መመሪያዎችን ያስሱ። ከማመልከትዎ በፊት እያንዳንዱን ዕድል በጥንቃቄ ያረጋግጡ።",
    security: "ለደህንነትዎ ትንሽ ማስታወሻ",
    securityBody:
      "የይለፍ ቃሎችን፣ የማረጋገጫ አገናኞችን ወይም ኮዶችን አያጋሩ። Sera የይለፍ ቃልዎን በኢሜይል አይጠይቅም። የገንዘብ፣ የመታወቂያ ወይም የተማሪ ሚስጥራዊ መረጃ በሙከራው አያስገቡ።",
    fallback: "አዝራሩ ካልተከፈተ ይህን አገናኝ ወደ አሳሽዎ ይቅዱ፦",
    help: "እገዛ ይፈልጋሉ?",
    helpBody:
      "ለመለያ ድጋፍ mahir@hisabtech.com ያግኙ። የይለፍ ቃልዎን ወይም የማረጋገጫ አገናኞችን አይላኩ።",
    footer: "ዕድሎች · ትምህርት · ግልጽ ቀጣይ እርምጃ",
    reason:
      "በSera ላይ በተደረገ የመለያ እርምጃ ምክንያት ይህን ኢሜይል ተቀብለዋል። ይህ የማስታወቂያ ምዝገባ አይደለም።",
    helpLink: "እገዛ እና የመለያ መረጃ",
    privacy: "የግላዊነት መረጃ",
  },
};
export function emailCopy(kind: EmailKind, locale: string) {
  return copy[locale === "am" ? "am" : "en"][kind];
}
export function renderEmail({
  kind,
  locale = "en",
  name = "there",
  actionUrl,
  origin,
  template = false,
}: {
  kind: EmailKind;
  locale?: string;
  name?: string;
  actionUrl: string;
  origin: string;
  template?: boolean;
}) {
  const language = supportedEmailLocales.includes(
    locale as (typeof supportedEmailLocales)[number],
  )
    ? locale
    : "en";
  const c =
    additionalEmailCopy[language] || copy[language === "am" ? "am" : "en"];
  const e = c[kind];
  const url = template ? actionUrl : escapeHtml(safeEmailUrl(actionUrl));
  const site = template ? origin : escapeHtml(safeEmailUrl(origin));
  const full = kind === "welcome" || kind === "confirmation";
  const body = `<p style="font-size:13px;color:#278198;font-weight:700;letter-spacing:1px">${c.eyebrow}</p><h1 style="font-size:32px;line-height:1.4;letter-spacing:-.8px;color:#163e4c;margin:18px 0">${e.heading}</h1><p style="font-size:16px;line-height:1.9;color:#42616e">${c.hello} ${template ? name : escapeHtml(name)},</p><p style="font-size:15px;line-height:1.9;color:#42616e">${e.body}</p><table role="presentation" cellspacing="0" cellpadding="0" style="margin:28px 0"><tr><td bgcolor="#087f9f" style="border-radius:12px"><a href="${url}" style="display:inline-block;padding:16px 25px;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700">${e.action} &rarr;</a></td></tr></table><p style="font-size:11px;line-height:1.8;color:#6b818b">${c.fallback}<br><a href="${url}" style="color:#087c99;word-break:break-all">${url}</a></p>${full ? `<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="margin-top:30px;border-top:1px solid #dcebf0"><tr><td style="padding:25px 0 12px"><h2 style="font-size:18px;color:#1f5365;margin:0 0 10px">${c.first}</h2><p style="font-size:14px;line-height:1.9;color:#526e7a;margin:0">${c.firstBody}</p></td></tr><tr><td style="padding:16px 0 25px"><h2 style="font-size:18px;color:#1f5365;margin:0 0 10px">${c.second}</h2><p style="font-size:14px;line-height:1.9;color:#526e7a;margin:0">${c.secondBody}</p></td></tr></table>` : ""}<table role="presentation" width="100%" cellspacing="0" cellpadding="0" bgcolor="#edf7fa" style="border:1px solid #d7eaf1;border-radius:14px;margin-top:24px"><tr><td style="padding:20px"><h2 style="font-size:14px;color:#22647a;margin:0 0 10px">${c.security}</h2><p style="font-size:12px;line-height:1.9;color:#466d7b;margin:0">${c.securityBody}</p></td></tr></table><h2 style="font-size:15px;color:#285766;margin:28px 0 8px">${c.help}</h2><p style="font-size:12px;line-height:1.9;color:#607c88">${c.helpBody}</p><a href="${site}/help" style="color:#087f9f;font-size:12px">${c.helpLink}</a>`;
  const html = `<!doctype html><html lang="${language}" dir="${language === "ar" ? "rtl" : "ltr"}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="light dark"><meta name="robots" content="noindex,nofollow"><title>${e.subject}</title></head><body style="margin:0;padding:0;background:#eef4f7;font-family:Arial,'Noto Sans Ethiopic','Nyala',sans-serif"><div style="display:none;max-height:0;overflow:hidden;opacity:0">${e.body}</div><table role="presentation" width="100%" cellspacing="0" cellpadding="0" bgcolor="#eef4f7"><tr><td align="center" style="padding:32px 12px"><table role="presentation" width="600" cellspacing="0" cellpadding="0" style="width:100%;max-width:600px;background:#ffffff;border:1px solid #dce8ed;border-radius:20px"><tr><td style="padding:32px 28px 24px;background:#e7f5fa;border-radius:20px 20px 0 0"><a href="${site}" dir="ltr" style="font-size:32px;font-weight:700;letter-spacing:-1.4px;text-decoration:none;color:#1b4352">Sera</a><p style="font-size:12px;line-height:1.8;color:#548090;margin:12px 0 0">${c.footer}</p></td></tr><tr><td aria-hidden="true" style="height:5px;background:#22a6c8;border-top:2px dotted #d8f2fa;border-bottom:2px dotted #c2e9f3"></td></tr><tr><td style="padding:28px">${body}</td></tr><tr><td style="padding:25px 28px;border-top:1px solid #e2edf1;background:#f8fbfc;border-radius:0 0 20px 20px"><p style="font-size:11px;color:#718792;line-height:1.8;margin:0 0 14px">${c.reason}</p><a href="${site}/privacy" style="font-size:11px;color:#48788a">${c.privacy}</a><p style="font-size:10px;line-height:1.8;color:#8a9ca5;margin:14px 0 0">Sera · ${new Date().getUTCFullYear()}</p></td></tr></table></td></tr></table></body></html>`;
  const text = [
    e.subject,
    `${c.hello} ${name},`,
    e.body,
    e.action,
    actionUrl,
    full ? `${c.first}\n${c.firstBody}\n${c.second}\n${c.secondBody}` : "",
    c.security,
    c.securityBody,
    c.help,
    `${origin}/help`,
    c.reason,
  ]
    .filter(Boolean)
    .join("\n\n");
  return { subject: e.subject, html, text, language };
}
export function safeEmailUrl(value: string) {
  const url = new URL(value);
  if (url.protocol !== "https:" || url.username || url.password)
    throw Error("Email links require HTTPS without credentials");
  return url.toString().replace(/\/$/, "");
}
