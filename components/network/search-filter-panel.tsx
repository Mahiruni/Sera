import Link from "next/link";
import { SlidersHorizontal } from "lucide-react";
import { workTypes } from "@/lib/recruitment/validation";
import { categoryFilters, searchHref, type SearchFilters } from "@/lib/network/search-filters";
import type { UniversalDiscoveryKind } from "@/lib/network/model";
export function SearchFilterPanel({kind,query,filters}:{kind:UniversalDiscoveryKind;query:string;filters:SearchFilters}) {
  const current=categoryFilters(kind,filters);
  return <details className="network-search-filters" open={Object.values(current).some(Boolean)}>
    <summary><SlidersHorizontal size={17} aria-hidden="true"/> Filters {Object.values(current).filter(Boolean).length||""}</summary>
    <form action="/discover" className="network-search-filter-form">
      <input type="hidden" name="tab" value={kind}/><input type="hidden" name="q" value={query}/>
      {kind!=="topics"&&<label>Location<input name="location" maxLength={80} defaultValue={current.location} placeholder="e.g. Addis Ababa"/></label>}
      {["all","jobs","companies"].includes(kind)&&<label>Employment type<select name="work" defaultValue={current.work}><option value="">Any type</option>{workTypes.map(t=><option key={t}>{t}</option>)}</select></label>}
      {["all","stories","projects","topics"].includes(kind)&&<label>Skill (exact tag)<input name="skill" maxLength={80} defaultValue={current.skill} placeholder="e.g. React"/></label>}
      {kind!=="people"&&<label>Published or posted<select name="days" defaultValue={current.days}><option value="">Any time</option><option value="7">Past 7 days</option><option value="30">Past 30 days</option><option value="90">Past 90 days</option></select></label>}
      <div className="network-search-filter-actions"><Link href={searchHref(kind,query)}>Clear filters</Link><button className="network-button" type="submit">Apply filters</button></div>
    </form>
    {kind==="all"&&<p className="network-search-caption">Filters apply to relevant categories. Employment type applies to jobs and companies; skills to posts, projects and topics; dates to published content and jobs.</p>}
  </details>;
}
