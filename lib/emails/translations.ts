type KindCopy = {
  subject: string;
  heading: string;
  body: string;
  action: string;
};
export type EmailCopy = {
  hello: string;
  eyebrow: string;
  first: string;
  firstBody: string;
  second: string;
  secondBody: string;
  security: string;
  securityBody: string;
  fallback: string;
  help: string;
  helpBody: string;
  footer: string;
  reason: string;
  helpLink: string;
  privacy: string;
  confirmation: KindCopy;
  recovery: KindCopy;
  welcome: KindCopy;
  password_changed: KindCopy;
};
// Initial product translations. Afar deliberately uses English until reviewed copy is available.
export const additionalEmailCopy: Record<string, EmailCopy> = {
  ar: {
    hello: "مرحبًا",
    eyebrow: "طريق أوضح. إمكانية جديدة.",
    first: "ابدأ بما يهمك",
    firstBody:
      "احتفظ ببيانات ملفك وتفضيلات اللغة في مكان خاص. المعلومات الإضافية اختيارية.",
    second: "استكشف بخطواتك",
    secondBody:
      "تصفح الوظائف التي ينشرها أصحاب العمل والأدلة العملية. تحقق من تفاصيل كل فرصة قبل التقديم.",
    security: "حافظ على أمان حسابك",
    securityBody:
      "لا تشارك كلمات المرور أو رموز وروابط التحقق. لن تطلب Sera كلمة مرورك عبر البريد. لا ترسل وثائق الهوية أو المعلومات المالية في المحادثات.",
    fallback: "إذا لم يعمل الزر، انسخ هذا الرابط إلى متصفحك:",
    help: "هل تحتاج إلى مساعدة؟",
    helpBody:
      "للمساعدة بشأن الحساب، تواصل مع mahir@hisabtech.com. لا ترسل كلمة المرور أو روابط التحقق.",
    footer: "فرص · تعلّم · خطوة تالية أوضح",
    reason:
      "تلقيت هذه الرسالة بسبب إجراء متعلق بحسابك في Sera. هذه ليست رسالة تسويقية.",
    helpLink: "المساعدة ومعلومات الحساب",
    privacy: "إشعار الخصوصية",
    confirmation: {
      subject: "أكد عنوان بريدك الإلكتروني في Sera",
      heading: "فصلك القادم يبدأ هنا.",
      body: "يرجى تأكيد عنوان بريدك الإلكتروني لإكمال إعداد حسابك. تساعدنا هذه الخطوة في التحقق من أنك تملك هذا العنوان.",
      action: "تأكيد بريدي الإلكتروني",
    },
    recovery: {
      subject: "إعادة تعيين كلمة مرور Sera",
      heading: "لنعد إلى مساحتك الخاصة.",
      body: "تلقينا طلبًا لإعادة تعيين كلمة مرورك. استخدم الرابط لاختيار كلمة مرور جديدة. إذا لم تطلب ذلك، فتجاهل هذه الرسالة.",
      action: "اختيار كلمة مرور جديدة",
    },
    welcome: {
      subject: "مرحبًا بك في Sera",
      heading: "أهلًا بك في خطوتك القادمة.",
      body: "تم تأكيد بريدك الإلكتروني. أضف تفاصيل ملفك، واحفظ تفضيلاتك، واستكشف الفرص المتاحة.",
      action: "إعداد ملفي الشخصي",
    },
    password_changed: {
      subject: "تم تغيير كلمة مرور Sera",
      heading: "تحديث لأمان حسابك.",
      body: "تم تغيير كلمة مرور حسابك. إذا أجريت هذا التغيير فلا يلزمك فعل شيء. إذا لم تكن أنت، فاطلب إعادة تعيين كلمة المرور من موقع Sera الرسمي.",
      action: "مراجعة أمان الحساب",
    },
  },
  om: {
    hello: "Akkam",
    eyebrow: "Karaa ifaa. Carraa haaraa.",
    first: "Waan siif barbaachisu irraa jalqabi",
    firstBody:
      "Odeeffannoo piroofaayilii fi filannoo afaanii kee bakka dhuunfaa tokko keessatti kaa’i. Odeeffannoon dabalataa dirqama miti.",
    second: "Suuta suutaan qoradhu",
    secondBody:
      "Hojiiwwan hojjechiistotaan maxxanfamanii fi qajeelfamoota ilaali. Osoo hin iyyatin dura odeeffannoo carraa sanaa mirkaneessi.",
    security: "Herrega kee eegi",
    securityBody:
      "Jecha iccitii, koodii ykn hidhaa mirkaneessaa hin qoodin. Sera jecha iccitii kee imeeliin si hin gaafatu. Sanada eenyummaa ykn odeeffannoo maallaqaa ergaadhaan hin ergini.",
    fallback:
      "Yoo qabduun hin banamne, hidhaa kana gara biraawzarii keetti garagalchi:",
    help: "Gargaarsa barbaaddaa?",
    helpBody:
      "Gargaarsa herregaaf mahir@hisabtech.com qunnami. Jecha iccitii ykn hidhaa mirkaneessaa hin ergini.",
    footer: "Carraa · Barnoota · Tarkaanfii itti aanu",
    reason:
      "Tarkaanfii herrega Sera kee irratti fudhatameen imeelii kana argatte. Kun ergaa beeksisaa miti.",
    helpLink: "Gargaarsa fi odeeffannoo herregaa",
    privacy: "Beeksisa dhuunfummaa",
    confirmation: {
      subject: "Imeelii Sera kee mirkaneessi",
      heading: "Boqonnaan kee itti aanu asitti jalqaba.",
      body: "Herrega kee qopheessuu xumuruuf teessoo imeelii kee mirkaneessi. Kun teessoon sun kan kee ta’uu mirkaneessa.",
      action: "Imeelii koo mirkaneessi",
    },
    recovery: {
      subject: "Jecha iccitii Sera kee haaromsi",
      heading: "Gara bakka keetti haa deebinu.",
      body: "Jecha iccitii kee haaromsuuf gaaffii arganneerra. Hidhaa kanaan jecha iccitii haaraa filadhu. Yoo ati hin gaafatin, imeelii kana dhiisuu dandeessa.",
      action: "Jecha iccitii haaraa filadhu",
    },
    welcome: {
      subject: "Baga gara Sera dhuftan",
      heading: "Boqonnaa kee haaraatti baga nagaan dhufte.",
      body: "Imeeliin kee mirkanaa’eera. Piroofaayilii kee qopheessi, filannoowwan kee kaa’i, carraawwan jiran qoradhu.",
      action: "Piroofaayilii koo qopheessi",
    },
    password_changed: {
      subject: "Jechi iccitii Sera kee jijjiirameera",
      heading: "Odeeffannoo nageenya herrega kee.",
      body: "Jechi iccitii herrega kee jijjiirameera. Yoo ati jijjiirte ta’e tarkaanfiin biraa hin barbaachisu. Yoo ati hin jijjiirin, marsariitii Sera irraa haaromsa jecha iccitii gaafadhu.",
      action: "Nageenya herregaa ilaali",
    },
  },
  ti: {
    hello: "ሰላም",
    eyebrow: "ንጹር መንገዲ። ሓድሽ ዕድል።",
    first: "ካብ ንዓኻ ኣገዳሲ ዝኾነ ጀምር",
    firstBody:
      "ሓበሬታ መግለጺኻን ምርጫ ቋንቋኻን ኣብ ሓደ ናይ ውልቂ ቦታ ኣቐምጥ። ተወሳኺ ሓበሬታ ግዴታ ኣይኮነን።",
    second: "ብናትካ ፍጥነት ዳህስስ",
    secondBody:
      "ብኣስራሕቲ ዝተዘርግሑ ስራሓትን ተግባራዊ መምርሒታትን ተመልከት። ቅድሚ ምምልካትካ ዝርዝር እቲ ዕድል ኣረጋግጽ።",
    security: "ድሕነት መለያኻ ሓሉ",
    securityBody:
      "መሕለፊ ቃል፣ ኮድ ወይ ናይ ምርግጋጽ መላግቦ ኣይተካፍል። Sera መሕለፊ ቃልካ ብኢመይል ኣይሓትትን። ሰነድ መንነት ወይ ሓበሬታ ገንዘብ ብመልእኽቲ ኣይትለኣኽ።",
    fallback: "እቲ መጠወቒ እንተዘይሰሪሑ፣ ነዚ መላግቦ ናብ መርበብ መዳህሰሲኻ ቅዳሕ:",
    help: "ሓገዝ የድልየካ ድዩ?",
    helpBody:
      "ንሓገዝ መለያ mahir@hisabtech.com ርኸብ። መሕለፊ ቃል ወይ ናይ ምርግጋጽ መላግቦ ኣይትለኣኽ።",
    footer: "ዕድላት · ትምህርቲ · ንጹር ቀጻሊ ስጉምቲ",
    reason:
      "ኣብ መለያ Sera ብዝተወስደ ስጉምቲ ምኽንያት ነዚ ኢመይል ተቐቢልካዮ። እዚ ናይ ማስታወቂያ መልእኽቲ ኣይኮነን።",
    helpLink: "ሓገዝን ሓበሬታ መለያን",
    privacy: "መግለጺ ውልቃዊ ሓበሬታ",
    confirmation: {
      subject: "ናይ Sera ኢመይልካ ኣረጋግጽ",
      heading: "ቀጻሊ ምዕራፍካ ኣብዚ ይጅምር።",
      body: "መለያኻ ንምድላው ንምዝዛም ኢመይልካ ኣረጋግጽ። እዚ እቲ ኣድራሻ ናትካ ምዃኑ የረጋግጽ።",
      action: "ኢመይለይ ኣረጋግጽ",
    },
    recovery: {
      subject: "ናይ Sera መሕለፊ ቃልካ ሓድስ",
      heading: "ናብ ናትካ ቦታ ንመለስካ።",
      body: "መሕለፊ ቃልካ ንምሕዳስ ሕቶ በጺሑና። በቲ መላግቦ ሓድሽ መሕለፊ ቃል ምረጽ። ነዚ እንተዘይሓቲትካ ነቲ ኢመይል ሸለል ክትብሎ ትኽእል።",
      action: "ሓድሽ መሕለፊ ቃል ምረጽ",
    },
    welcome: {
      subject: "እንቋዕ ናብ Sera ብደሓን መጻእካ",
      heading: "ናብ ቀጻሊ ስጉምትኻ እንቋዕ ብደሓን መጻእካ።",
      body: "ኢመይልካ ተረጋጊጹ። መግለጺኻ ኣዳሉ፣ ምርጫታትካ ኣቐምጥ፣ ዘለዉ ዕድላት ዳህስስ።",
      action: "መግለጸይ ኣዳሉ",
    },
    password_changed: {
      subject: "ናይ Sera መሕለፊ ቃልካ ተቐይሩ",
      heading: "ናይ መለያኻ ድሕነት ሓበሬታ።",
      body: "መሕለፊ ቃል መለያኻ ተቐይሩ። ነዚ ንስኻ እንተጌርካዮ ካልእ ስጉምቲ ኣየድልን። ንስኻ እንተዘይቀይርካዮ፣ ካብ ወግዓዊ መርበብ Sera መሕለፊ ቃል ንምሕዳስ ሕተት።",
      action: "ድሕነት መለያ ተመልከት",
    },
  },
  so: {
    hello: "Salaan",
    eyebrow: "Jid cad. Fursad cusub.",
    first: "Ku bilow waxa kuu muhiimka ah",
    firstBody:
      "Ku hay xogta astaantaada iyo doorashada luqadda hal meel oo gaar kuu ah. Xogta dheeraadka ahi waa ikhtiyaari.",
    second: "Si tartiib ah u sahami",
    secondBody:
      "Eeg shaqooyinka loo-shaqeeyayaashu soo geliyaan iyo hagayaasha waxtarka leh. Hubi faahfaahinta fursad kasta ka hor intaadan codsan.",
    security: "Ilaali amniga akoonkaaga",
    securityBody:
      "Ha wadaagin erayga sirta ah, koodhadhka ama xiriiriyeyaasha xaqiijinta. Sera kuma weydiin doonto eraygaaga sirta ah iimayl ahaan. Ha ku dirin aqoonsi ama xog maaliyadeed farriimaha.",
    fallback:
      "Haddii badhanku shaqayn waayo, ku koobi xiriiriyahan biraawsarkaaga:",
    help: "Ma u baahan tahay caawimo?",
    helpBody:
      "Caawimada akoonka kala xiriir mahir@hisabtech.com. Ha dirin erayga sirta ah ama xiriiriyeyaasha xaqiijinta.",
    footer: "Fursado · Waxbarasho · Tallaabo xigta oo cad",
    reason:
      "Waxaad heshay iimaylkan sababtoo ah tallaabo laga qaaday akoonkaaga Sera. Tani ma aha farriin suuqgeyn.",
    helpLink: "Caawimo iyo xogta akoonka",
    privacy: "Ogeysiiska asturnaanta",
    confirmation: {
      subject: "Xaqiiji cinwaanka iimaylkaaga Sera",
      heading: "Cutubkaaga xiga wuxuu ka bilaabmaa halkan.",
      body: "Fadlan xaqiiji cinwaanka iimaylkaaga si aad u dhammaystirto dejinta akoonka. Tallaabadani waxay xaqiijinaysaa inaad leedahay cinwaankaas.",
      action: "Xaqiiji iimaylkayga",
    },
    recovery: {
      subject: "Dib u deji eraygaaga sirta ah ee Sera",
      heading: "Aan kugu celinno goobtaada.",
      body: "Waxaan helnay codsi dib loogu dejinayo eraygaaga sirta ah. Adeegso xiriiriyaha si aad u doorato mid cusub. Haddii aadan codsan, waad iska indho tiri kartaa iimaylkan.",
      action: "Dooro eray sir ah oo cusub",
    },
    welcome: {
      subject: "Ku soo dhowow Sera",
      heading: "Ku soo dhowow tallaabadaada xigta.",
      body: "Iimaylkaaga waa la xaqiijiyey. Diyaari astaantaada, kaydi doorashooyinkaaga oo sahami fursadaha jira.",
      action: "Diyaari astaantayda",
    },
    password_changed: {
      subject: "Eraygaaga sirta ah ee Sera waa la beddelay",
      heading: "Warbixin ku saabsan amniga akoonkaaga.",
      body: "Erayga sirta ah ee akoonkaaga waa la beddelay. Haddii adigu beddeshay, tallaabo kale looma baahna. Haddii aadan adigu beddelin, ka codso dib u dejin bogga rasmiga ah ee Sera.",
      action: "Eeg amniga akoonka",
    },
  },
};
