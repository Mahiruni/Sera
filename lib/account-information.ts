// Service information for the account release. Recruitment demo records stay local.
export const accountInformationPages = {
  privacy: {
    title: ["Privacy notice", "የግላዊነት መግለጫ"],
    intro: [
      "Effective 7 November 2026. How Sera handles your account and profile information.",
      "ከ7 November 2026 ጀምሮ። Sera የመለያዎን እና የመገለጫዎን መረጃ እንዴት እንደሚያስተናግድ።",
    ],
    sections: [
      {
        title: ["Who operates Sera", "Seraን የሚያስተዳድረው"],
        body: [
          "Sera is operated by Mahir Aman, Ethiopia. For account support or privacy requests, email mahir@hisabtech.com. Sera is a product name; this notice does not claim a registered company or recruitment licence.",
          "Seraን በኢትዮጵያ Mahir Aman ያስተዳድራል። ለመለያ ድጋፍ ወይም ለግላዊነት ጥያቄ mahir@hisabtech.com ይጻፉ። Sera የምርት ስም ነው፤ ይህ መግለጫ የተመዘገበ ድርጅት ወይም የቅጥር ፈቃድ አለ አይልም።",
        ],
      },
      {
        title: ["Information you provide", "እርስዎ የሚሰጡት መረጃ"],
        body: [
          "Registration collects your name, email, password, language and candidate/employer preference. Supabase Auth manages your password; it is not stored in your profile. You may optionally add location, phone, skills, experience, preferred roles or organization details. Only provide information you want stored in your private account. Do not upload identity documents, financial details or confidential student records.",
          "ምዝገባ ስም፣ ኢሜይል፣ የይለፍ ቃል፣ ቋንቋ እና የሥራ ፈላጊ/ቀጣሪ ምርጫ ይሰበስባል። Supabase Auth የይለፍ ቃልዎን ያስተዳድራል፤ በመገለጫዎ አይቀመጥም። አካባቢ፣ ስልክ፣ ክህሎት፣ ልምድ፣ የሚፈልጉት ሥራ ወይም የድርጅት ዝርዝር እንደ አማራጭ ማከል ይችላሉ። የማንነት ሰነዶችን፣ የገንዘብ ወይም ሚስጥራዊ የተማሪ መረጃ አያስገቡ።",
        ],
      },
      {
        title: ["Why we use it", "ለምን እንጠቀምበታለን"],
        body: [
          "We use this information to create and secure your account, save your private profile and preferences, send verification/recovery/security emails, and respond to your requests. Registration does not subscribe you to marketing. Profiles are not publicly listed or shared with employers in this release. Choosing employer does not grant verified or administrator access.",
          "መረጃውን መለያዎን ለመፍጠርና ለመጠበቅ፣ የግል መገለጫና ምርጫዎችን ለማስቀመጥ፣ የማረጋገጫ፣ የማገገሚያ እና የደህንነት ኢሜይሎችን ለመላክ እና ጥያቄዎችን ለመመለስ እንጠቀማለን። ምዝገባ የማስታወቂያ ደንበኝነት አያስጀምርም። መገለጫዎች በዚህ ስሪት በይፋ አይታዩም፣ ከቀጣሪዎችም ጋር አይጋሩም።",
        ],
      },
      {
        title: [
          "Service providers and international processing",
          "አቅራቢዎች እና ከሀገር ውጭ ማስኬድ",
        ],
        body: [
          "Vercel hosts the website, Supabase provides authentication and the account database in Frankfurt, Germany, and Hostinger delivers account emails. These providers process the information needed to supply their services, including ordinary request and security logs. Account information and email may be processed outside Ethiopia. We do not sell your profile or send it to an AI provider in this release.",
          "Vercel ድረ ገጹን ያስተናግዳል፣ Supabase በፍራንክፈርት፣ ጀርመን የመለያ ውሂብ ጎታና ማረጋገጫ ያቀርባል፣ Hostinger የመለያ ኢሜይሎችን ያደርሳል። አቅራቢዎቹ አገልግሎታቸውን ለመስጠት የሚያስፈልገውን መረጃ ያስኬዳሉ። የመለያ መረጃና ኢሜይል ከኢትዮጵያ ውጭ ሊስተናገዱ ይችላሉ። መገለጫዎን አንሸጥም፣ በዚህ ስሪት ወደ AI አቅራቢም አንልክም።",
        ],
      },
      {
        title: ["Storage and your choices", "ማከማቻ እና ምርጫዎችዎ"],
        body: [
          "Account data is retained while your account exists. Edit optional details in Settings. To request access, correction or deletion, contact mahir@hisabtech.com from your account email; we verify ownership before acting. Deletion requests are handled manually; backup and security-log copies may remain until the providers' retention cycles expire. Session cookies keep you signed in. Language preferences and clearly labelled recruitment demo data use browser storage; clearing that storage does not delete your hosted account. You can clear demo data from the demo account page.",
          "መለያዎ እስካለ ድረስ የመለያ መረጃ ይቀመጣል። አማራጭ ዝርዝሮችን በቅንብሮች ያስተካክሉ። የመረጃ ቅጂ፣ እርማት ወይም ስረዛ ለመጠየቅ ከመለያ ኢሜይልዎ ወደ mahir@hisabtech.com ይጻፉ፤ ባለቤትነትዎን እናረጋግጣለን። ስረዛ በእጅ ይከናወናል፤ ምትኬዎችና የደህንነት መዝገቦች አቅራቢዎቹ እስኪያስወግዷቸው ሊቆዩ ይችላሉ። የአሳሽ ማከማቻ ማጽዳት የመስመር ላይ መለያዎን አይሰርዝም።",
        ],
      },
    ],
  },
  terms: {
    title: ["Account terms", "የመለያ ውሎች"],
    intro: [
      "Effective 7 November 2026. These terms cover Sera accounts and the separate recruitment demonstration.",
      "ከ7 November 2026 ጀምሮ። እነዚህ ውሎች የSera መለያዎችን እና የተለየውን የቅጥር ሙከራ ይመለከታሉ።",
    ],
    sections: [
      {
        title: ["What is available", "ያሉ አገልግሎቶች"],
        body: [
          "You can create a free personal account, verify your email and manage a private profile and preferences. Sera is operated by Mahir Aman in Ethiopia; account questions can be sent to mahir@hisabtech.com. Job listings, applications, employer workspaces and messages labelled demo remain sample, browser-local features. No employment, admission, employer verification, interview, payment or application delivery is promised.",
          "ነፃ የግል መለያ መፍጠር፣ ኢሜይልዎን ማረጋገጥ እና የግል መገለጫና ምርጫዎችን ማስተዳደር ይችላሉ። Seraን Mahir Aman በኢትዮጵያ ያስተዳድራል፤ የመለያ ጥያቄዎችን ወደ mahir@hisabtech.com ይላኩ። ሙከራ ተብለው የተለዩ ሥራዎች፣ ማመልከቻዎችና መልዕክቶች በአሳሽ ብቻ ያሉ ምሳሌዎች ናቸው። ቅጥር፣ ቅበላ፣ የቀጣሪ ማረጋገጫ፣ ቃለ መጠይቅ ወይም ክፍያ አይሰጡም።",
        ],
      },
      {
        title: ["Your responsibilities", "ኃላፊነቶችዎ"],
        body: [
          "Use an email address you control and keep your password private. Provide information you are entitled to share. Do not impersonate others, submit unlawful content, abuse account emails, attempt unauthorized access or interfere with the service. Do not put identity documents, banking details or confidential recruitment or student material in profiles or demo records. Candidate/employer is a preference and does not confer privileged access.",
          "የሚቆጣጠሩትን ኢሜይል ይጠቀሙ፣ የይለፍ ቃልዎንም በሚስጥር ይያዙ። ለማጋራት መብት ያለዎትን መረጃ ብቻ ያቅርቡ። ሌሎችን አይምሰሉ፣ ሕገወጥ ይዘት አያስገቡ፣ ያልተፈቀደ መዳረሻ አይሞክሩ፣ አገልግሎቱንም አያውኩ። የማንነት፣ የባንክ ወይም ሚስጥራዊ መረጃ አያስገቡ። የሥራ ፈላጊ/ቀጣሪ ምርጫ ልዩ የመዳረሻ መብት አይሰጥም።",
        ],
      },
      {
        title: [
          "Email, availability and account closure",
          "ኢሜይል፣ አገልግሎት እና መለያ መዝጋት",
        ],
        body: [
          "We send transactional verification, recovery and security emails. Some languages currently use English email fallback, disclosed during signup. Registering does not enroll you in marketing or a paid plan. Features may change or be interrupted; we do not guarantee uninterrupted availability or recruitment outcomes. We may restrict accounts that abuse the service. To request account closure or help, contact mahir@hisabtech.com. These terms do not exclude rights that applicable law does not permit us to exclude. Material changes will be described on this page.",
          "የማረጋገጫ፣ የማገገሚያ እና የደህንነት ኢሜይሎችን እንልካለን። አንዳንድ ቋንቋዎች በምዝገባ እንደሚገለጸው የእንግሊዝኛ ኢሜይል ይጠቀማሉ። ምዝገባ የማስታወቂያ ወይም የክፍያ ደንበኝነት አይፈጥርም። አገልግሎቶች ሊቀየሩ ወይም ሊቋረጡ ይችላሉ። አገልግሎቱን የሚያጎሳቁሉ መለያዎች ሊገደቡ ይችላሉ። ለመለያ መዝጋት ወይም ድጋፍ mahir@hisabtech.com ያግኙ። በሕግ የተሰጡ የማይገደቡ መብቶችዎ አይቀሩም።",
        ],
      },
    ],
  },
  contact: {
    title: ["Contact Sera", "Seraን ያግኙ"],
    intro: [
      "Account support and privacy requests.",
      "የመለያ ድጋፍ እና የግላዊነት ጥያቄዎች።",
    ],
    sections: [
      {
        title: ["Email the operator", "አስተዳዳሪውን በኢሜይል ያግኙ"],
        body: [
          "Contact Mahir Aman at mahir@hisabtech.com. For account changes, write from the email used for your account. Never include your password or a verification link. Requests are handled manually; this is not an emergency service. Demo messages remain in your browser and do not reach support.",
          "Mahir Amanን በmahir@hisabtech.com ያግኙ። ለመለያ ለውጥ ከመለያዎ ኢሜይል ይጻፉ። የይለፍ ቃል ወይም የማረጋገጫ አገናኝ አያካትቱ። ጥያቄዎች በእጅ ይስተናገዳሉ፤ ይህ የአደጋ ጊዜ አገልግሎት አይደለም። የሙከራ መልዕክቶች ድጋፍ አይደርሱም።",
        ],
      },
    ],
  },
} as const;
