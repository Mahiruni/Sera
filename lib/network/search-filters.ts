import { sanitizeDiscoveryQuery, type UniversalDiscoveryKind } from "./model";
import { workTypes } from "../recruitment/validation";
export type SearchFilters = { location: string; work: string; skill: string; days: string };
export const emptySearchFilters: SearchFilters = {location:"",work:"",skill:"",days:""};
export function searchFilters(params: Record<string,unknown>): SearchFilters {
  return {location:sanitizeDiscoveryQuery(params.location),skill:sanitizeDiscoveryQuery(params.skill),work:typeof params.work==="string"&&workTypes.some(t=>t===params.work)?params.work:"",days:typeof params.days==="string"&&["7","30","90"].includes(params.days)?params.days:""};
}
export function categoryFilters(kind: UniversalDiscoveryKind, filters: SearchFilters): SearchFilters {
  if(kind==="all") return filters;
  return {location:kind==="topics"?"":filters.location,work:kind==="jobs"||kind==="companies"?filters.work:"",skill:kind==="stories"||kind==="projects"||kind==="topics"?filters.skill:"",days:kind==="people"?"":filters.days};
}
export function searchHref(kind:UniversalDiscoveryKind, query:string, filters:SearchFilters=emptySearchFilters) {
  const p=new URLSearchParams({tab:kind});
  if(query) p.set("q",query);
  for(const [key,value] of Object.entries(categoryFilters(kind,filters))) if(value) p.set(key,value);
  return "/discover?"+p.toString();
}
export function searchSince(days:string, now=Date.now()) {
  return ["7","30","90"].includes(days)?new Date(now-Number(days)*86400000).toISOString():null;
}
export function rankSearchRows<T>(rows:T[], query:string, label:(row:T)=>string) {
  const q=query.toLocaleLowerCase();
  const score=(s:string)=>{const t=s.toLocaleLowerCase();return t===q?0:t.startsWith(q)?1:t.includes(q)?2:3;};
  return q?[...rows].sort((a,b)=>score(label(a))-score(label(b))):rows;
}
