export const postAudiences=["public","connections","followers","only_me"] as const;
export type PostAudience=typeof postAudiences[number];
export const audienceLabels:Record<PostAudience,string>={public:"Public",connections:"Connections",followers:"Followers",only_me:"Only me"};
export function readAudience(value:unknown):PostAudience|null{return value===null||value===undefined||value===""?"public":typeof value==="string"&&postAudiences.includes(value as PostAudience)?value as PostAudience:null;}
