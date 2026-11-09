import { readFileSync, writeFileSync } from "node:fs";
const root = process.argv[2] ?? ".";
function patch(file, pairs) {
  const p = `${root}/${file}`;
  let s = readFileSync(p, "utf8");
  for (const [from, to, marker] of pairs) {
    if (s.includes(marker)) continue; // already patched
    if (s.split(from).length !== 2) throw new Error(`${file}: anchor not found exactly once: ${from.slice(0, 50)}`);
    s = s.replace(from, to);
  }
  writeFileSync(p, s);
  console.log("patched", file);
}
patch("components/auth/forms.tsx", [
  ['import { useSera } from "../store";', 'import { useSera } from "../store";\nimport LinkedInButton from "./LinkedInButton";', "./LinkedInButton"],
  ['        </form>\n        <p className="auth-footnote">',
   '        </form>\n        {isEntry && <LinkedInButton mode={mode === "signup" ? "signup" : "signin"} />}\n        <p className="auth-footnote">', "<LinkedInButton mode"],
]);
patch("lib/auth/validation.ts", [
  ['"/reset-password"].includes(', '"/reset-password", "/settings/linkedin"].includes(', "/settings/linkedin"],
]);
