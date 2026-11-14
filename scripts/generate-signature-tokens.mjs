import fs from "node:fs";
const tokens=JSON.parse(fs.readFileSync("brand/sera-tokens.json","utf8"));
const css=Object.entries(tokens.semantic).map(([mode,roles])=>`.signature-system[data-theme="${mode}"]{${Object.entries(roles).map(([k,v])=>`--sig-${k.replace(/[A-Z]/g,x=>"-"+x.toLowerCase())}:${v};`).join("")}}`).join("\n")+"\n";
const kt=`package et.sera.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Generated from brand/sera-tokens.json; use only on Signature components. */
internal data class SignatureColors(${Object.keys(tokens.semantic.light).map(k=>`val ${k}:Color`).join(",")})
internal fun signatureColors(dark:Boolean):SignatureColors=if(dark) SignatureColors(
${Object.entries(tokens.semantic.dark).map(([k,v])=>`    ${k}=Color(0xFF${v.slice(1)})`).join(",\n")}
) else SignatureColors(
${Object.entries(tokens.semantic.light).map(([k,v])=>`    ${k}=Color(0xFF${v.slice(1)})`).join(",\n")}
)
internal object SignatureMetrics {
    val gap=${tokens.metrics.space[1]}.dp
    val padding=${tokens.metrics.space[3]}.dp
    val radius=${tokens.metrics.radius.medium}.dp
    val largeRadius=${tokens.metrics.radius.large}.dp
    val target=${tokens.metrics.target.android}.dp
    val body=${tokens.metrics.type.body}.sp
    val metadata=${tokens.metrics.type.metadata}.sp
    val heading=${tokens.metrics.type.heading}.sp
}
`;
const outputs={"components/signature/signature-tokens.css":css,"android-native/app/src/main/java/et/sera/nativeapp/SeraSignatureTokens.kt":kt};
for(const [path,content] of Object.entries(outputs)){if(process.argv.includes("--check")){if(fs.readFileSync(path,"utf8")!==content)throw Error("Stale Signature tokens: "+path);}else fs.writeFileSync(path,content);}
