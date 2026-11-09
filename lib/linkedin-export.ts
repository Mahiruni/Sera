/** Reads a LinkedIn "Get a copy of your data" .zip entirely in the browser. */
export type ExportKey = "headline" | "summary" | "location" | "skills" | "experience" | "education";
export type ExportResult = { fields: Partial<Record<ExportKey, string>>; truncated: ExportKey[] };

const WANTED = new Set(["profile.csv", "positions.csv", "education.csv", "skills.csv"]);
const MAX_ENTRY = 20_000_000;

function csv(input: string): string[][] {
  const text = input.replace(/^\uFEFF/, "");
  const rows: string[][] = [];
  let row: string[] = [];
  let cur = "";
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"') {
        if (text[i + 1] === '"') { cur += '"'; i++; } else quoted = false;
      } else cur += c;
    } else if (c === '"') quoted = true;
    else if (c === ",") { row.push(cur); cur = ""; }
    else if (c === "\n" || c === "\r") {
      if (c === "\r" && text[i + 1] === "\n") i++;
      row.push(cur); rows.push(row); row = []; cur = "";
    } else cur += c;
  }
  if (cur || row.length) { row.push(cur); rows.push(row); }
  return rows;
}

function records(text: string | undefined, required: string): Record<string, string>[] {
  if (!text) return [];
  const rows = csv(text);
  const h = rows.findIndex((r) => r.includes(required));
  if (h < 0) return [];
  const head = rows[h];
  return rows
    .slice(h + 1)
    .filter((r) => r.some((c) => c.trim()))
    .map((r) => Object.fromEntries(head.map((k, i) => [k, (r[i] ?? "").trim()])));
}

async function readZip(file: Blob): Promise<Map<string, string>> {
  const tail = await file.slice(Math.max(0, file.size - 66_000), file.size).arrayBuffer();
  const tv = new DataView(tail);
  let eocd = -1;
  for (let i = tail.byteLength - 22; i >= 0; i--)
    if (tv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  if (eocd < 0) throw new Error("Not a zip file");
  const count = tv.getUint16(eocd + 10, true);
  const cdSize = tv.getUint32(eocd + 12, true);
  const cdOff = tv.getUint32(eocd + 16, true);
  if (cdOff === 0xffffffff) throw new Error("Zip64 is not supported");
  const cd = await file.slice(cdOff, cdOff + cdSize).arrayBuffer();
  const dv = new DataView(cd);
  const dec = new TextDecoder();
  const out = new Map<string, string>();
  let p = 0;
  for (let n = 0; n < count && p + 46 <= cd.byteLength; n++) {
    if (dv.getUint32(p, true) !== 0x02014b50) break;
    const method = dv.getUint16(p + 10, true);
    const size = dv.getUint32(p + 20, true);
    const nl = dv.getUint16(p + 28, true);
    const el = dv.getUint16(p + 30, true);
    const cl = dv.getUint16(p + 32, true);
    const off = dv.getUint32(p + 42, true);
    const name = dec.decode(new Uint8Array(cd, p + 46, nl));
    p += 46 + nl + el + cl;
    const base = name.split("/").pop()!.toLowerCase();
    if (!WANTED.has(base) || out.has(base) || size > MAX_ENTRY) continue;
    const lh = new DataView(await file.slice(off, off + 30).arrayBuffer());
    const start = off + 30 + lh.getUint16(26, true) + lh.getUint16(28, true);
    const data = file.slice(start, start + size);
    if (method === 0) out.set(base, await data.text());
    else if (method === 8) {
      const stream = data.stream().pipeThrough(new DecompressionStream("deflate-raw"));
      out.set(base, await new Response(stream as unknown as BodyInit).text());
    }
  }
  return out;
}

function lines(items: string[], max: number): { text: string; cut: boolean } {
  let text = "";
  for (const item of items) {
    const next = text ? `${text}\n${item}` : item;
    if (next.length > max) return { text, cut: true };
    text = next;
  }
  return { text, cut: false };
}

const span = (a?: string, b?: string) => (a || b ? ` (${a || "?"} – ${b || "present"})` : "");

export async function parseLinkedInExport(file: Blob): Promise<ExportResult> {
  const files = await readZip(file);
  const fields: ExportResult["fields"] = {};
  const truncated: ExportKey[] = [];
  const profile = records(files.get("profile.csv"), "Headline")[0];
  if (profile) {
    if (profile["Headline"]) fields.headline = profile["Headline"].slice(0, 120);
    if (profile["Summary"]) fields.summary = profile["Summary"].slice(0, 2000);
    if (profile["Geo Location"]) fields.location = profile["Geo Location"].slice(0, 500);
  }
  const skills = records(files.get("skills.csv"), "Name").map((r) => r["Name"]).filter(Boolean);
  if (skills.length) {
    let text = "";
    for (const s of skills) {
      const next = text ? `${text}, ${s}` : s;
      if (next.length > 500) { truncated.push("skills"); break; }
      text = next;
    }
    fields.skills = text;
  }
  const jobs = records(files.get("positions.csv"), "Company Name")
    .filter((r) => r["Title"] || r["Company Name"])
    .map((r) => `${r["Title"] || "Role"} — ${r["Company Name"] || "Company"}${span(r["Started On"], r["Finished On"])}`);
  if (jobs.length) {
    const { text, cut } = lines(jobs, 500);
    if (text) fields.experience = text;
    if (cut) truncated.push("experience");
  }
  const edu = records(files.get("education.csv"), "School Name")
    .filter((r) => r["School Name"])
    .map((r) => `${r["Degree Name"] ? `${r["Degree Name"]}, ` : ""}${r["School Name"]}${span(r["Start Date"], r["End Date"])}`);
  if (edu.length) {
    const { text, cut } = lines(edu, 2000);
    if (text) fields.education = text;
    if (cut) truncated.push("education");
  }
  return { fields, truncated };
}
