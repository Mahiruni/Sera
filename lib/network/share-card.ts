/** Link-only cards intentionally contain no private profile or job metadata. */
export const SERA_ORIGIN="https://sera.hisabtech.com";
const UUID="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
const pathPattern=new RegExp("^/(members|portfolio|jobs|posts)/("+UUID+")/?$","i");
const labels={members:"Professional profile",portfolio:"Portfolio",jobs:"Job opportunity",posts:"SERA post"} as const;
export type ShareCard={url:string;label:string;kind:"professional"|"location"};
export function professionalShare(raw:string):ShareCard|null{
  try{const u=new URL(raw.trim());if(u.origin!==SERA_ORIGIN||u.username||u.password||u.search||u.hash)return null;
    const m=pathPattern.exec(u.pathname);if(!m)return null;const type=m[1].toLowerCase() as keyof typeof labels;
    return {url:SERA_ORIGIN+"/"+type+"/"+m[2].toLowerCase(),label:labels[type],kind:"professional"};
  }catch{return null;}
}
export function locationShare(lat:string,lng:string):ShareCard|null{
  const decimal=/^[+-]?(?:\d+(?:\.\d+)?|\.\d+)$/;
  if(!decimal.test(lat.trim())||!decimal.test(lng.trim()))return null;
  const a=Number(lat),b=Number(lng);if(!Number.isFinite(a)||!Number.isFinite(b)||Math.abs(a)>90||Math.abs(b)>180)return null;
  return {url:"https://www.google.com/maps/search/?api=1&query="+a.toFixed(6)+","+b.toFixed(6),label:"Shared location",kind:"location"};
}
export function messageShareCards(body:string):ShareCard[]{
  const cards:ShareCard[]=[];
  for(const line of body.split("\n")){
    const raw=line.trim();let card=professionalShare(raw);
    if(!card){const m=/^https:\/\/www\.google\.com\/maps\/search\/\?api=1&query=([+-]?\d+(?:\.\d+)?),([+-]?\d+(?:\.\d+)?)$/.exec(raw);if(m)card=locationShare(m[1],m[2]);}
    if(card&&!cards.some(c=>c.url===card!.url))cards.push(card);
    if(cards.length===4)break;
  }
  return cards;
}

/** Portable plain-text contact: explicit selection, never sync an address book. */
export function contactShare(name:string,phone:string):string|null{
  const n=name.trim(),p=phone.trim();
  if(!n||n.length>100||/[\x00-\x1f\x7f]/.test(n)||!/^\+?[0-9][0-9 ().-]{2,29}$/.test(p)||p.replace(/\D/g,"").length<3)return null;
  return "Contact: "+n+"\nPhone: "+p;
}
export function messageContact(body:string):{name:string;phone:string}|null{
  const match=/(?:^|\n)Contact: ([^\n]+)\nPhone: ([^\n]+)(?:\n|$)/.exec(body);
  return match&&contactShare(match[1],match[2])?{name:match[1],phone:match[2]}:null;
}
