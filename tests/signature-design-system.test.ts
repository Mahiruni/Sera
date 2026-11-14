import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {execFileSync} from "node:child_process";
const tokens=JSON.parse(fs.readFileSync("brand/sera-tokens.json","utf8"));
function luminance(hex:string){const c=hex.slice(1).match(/../g)!.map(v=>parseInt(v,16)/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4);return .2126*c[0]+.7152*c[1]+.0722*c[2]}
function contrast(a:string,b:string){const x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05)}
test("Signature tokens stay synchronized across platforms",()=>{execFileSync(process.execPath,["scripts/generate-signature-tokens.mjs","--check"]);assert.equal(tokens.palette.purple,"#7C3AED");assert.equal(tokens.identity.direction,"03-signature")});
test("Signature essential text contrast meets AA",()=>{for(const [mode,c] of Object.entries(tokens.semantic) as [string,Record<string,string>][])for(const [a,b] of [["text","surface"],["muted","surface"],["onPrimary","primary"],["onActivity","activity"],["activityInk","activityContainer"],["call","surface"]])assert.ok(contrast(c[a],c[b])>=4.5,mode+" "+a+"/"+b)});
