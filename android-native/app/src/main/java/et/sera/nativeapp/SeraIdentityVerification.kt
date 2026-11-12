package et.sera.nativeapp

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal fun identityBadges(token:String,ids:List<String>):Set<String>{
 if(ids.isEmpty()||token.isBlank())return emptySet()
 return ids.distinct().chunked(200).flatMap{chunk->spaceRows(spaceCall(token,"sera_identity_badges",JSONObject().put("p_ids",JSONArray(chunk))).first).map{it.optString("id")}}.toSet()
}
@Composable internal fun SeraIdentityBadge(modifier:Modifier=Modifier){
 val teal=Color(0xFF087E83)
 Canvas(modifier.size(17.dp).semantics{contentDescription="Identity verified by SERA"}){
  val c=size.minDimension/2
  val path=Path().apply{for(i in 0..15){val a=(i*22.5-90)*Math.PI/180;val r=if(i%2==0)c else c*.78f;val x=c+(Math.cos(a)*r).toFloat();val y=c+(Math.sin(a)*r).toFloat();if(i==0)moveTo(x,y)else lineTo(x,y)};close()}
  drawPath(path,teal);drawLine(Color.White,Offset(c*.48f,c),Offset(c*.86f,c*1.35f),strokeWidth=1.7.dp.toPx(),cap=StrokeCap.Round);drawLine(Color.White,Offset(c*.86f,c*1.35f),Offset(c*1.53f,c*.64f),strokeWidth=1.7.dp.toPx(),cap=StrokeCap.Round)
 }
}
private fun identityJson(token:String,rpc:String,p:JSONObject=JSONObject()):JSONObject? = spaceCall(token,rpc,p).first?.let{runCatching{JSONObject(it)}.getOrNull()}
private fun evidenceCall(token:String,path:String,mime:String?=null,bytes:ByteArray?=null):Boolean{
 if(!Regex("^[0-9a-f-]{36}/[0-9a-f-]{36}/evidence$").matches(path))return false
 var c:HttpURLConnection?=null
 return try{
  c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/storage/v1/object/sera-identity-private"+if(bytes!=null)"/"+path else "").openConnection() as HttpURLConnection
  c.requestMethod=if(bytes!=null)"POST" else "DELETE";c.connectTimeout=15000;c.readTimeout=30000
  c.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);c.setRequestProperty("Authorization","Bearer $token");c.doOutput=true
  c.setRequestProperty("Content-Type",mime?:"application/json");c.setRequestProperty("x-upsert","false")
  val payload=bytes?:JSONObject().put("prefixes",JSONArray(listOf(path))).toString().toByteArray()
  c.setFixedLengthStreamingMode(payload.size);c.outputStream.use{it.write(payload)}
  c.responseCode in 200..299
 }catch(_:Exception){false}finally{c?.disconnect()}
}
private fun submitNativeIdentity(context:Context,token:String,type:String,uri:Uri):String{
 val mime=context.contentResolver.getType(uri)?:return "Unsupported document."
 if(mime !in listOf("image/jpeg","image/png","application/pdf"))return "Choose a JPG, PNG or PDF."
 val bytes=try{context.contentResolver.openInputStream(uri)?.use{input->val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384);while(true){val n=input.read(buffer);if(n<0)break;if(output.size()+n>3*1024*1024)return "Document must be 3 MB or smaller.";output.write(buffer,0,n)};output.toByteArray()}}catch(_:Exception){null}?:return "Unable to read document."
 val valid=when(mime){"application/pdf"->bytes.size>=5&&String(bytes,0,5,Charsets.US_ASCII)=="%PDF-";"image/jpeg"->bytes.size>=3&&(bytes[0].toInt() and 255)==255&&(bytes[1].toInt() and 255)==216&&(bytes[2].toInt() and 255)==255;else->bytes.size>=4&&(bytes[0].toInt() and 255)==137&&bytes[1].toInt()==80&&bytes[2].toInt()==78&&bytes[3].toInt()==71}
 if(!valid)return "Invalid document format."
 val draft=identityJson(token,"sera_identity_start",JSONObject().put("p_document_type",type).put("p_consent",true))?:return "Unable to open request. Check your current request."
 if(!evidenceCall(token,draft.optString("path"),mime,bytes))return "Upload failed or evidence already exists. Submit the saved draft or withdraw it."
 if(spaceCall(token,"sera_identity_submit",JSONObject().put("p_id",draft.optString("id"))).first==null)return "Evidence uploaded. Submit the saved draft."
 return ""
}
@Composable internal fun SeraIdentityVerification(token:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var state by remember(token){mutableStateOf<JSONObject?>(null)}
 var history by remember(token){mutableStateOf<List<JSONObject>>(emptyList())}
 var historicalEvidence by remember(token){mutableStateOf<String?>(null)}
 var type by remember(token){mutableStateOf("national_id")}
 var selected by remember(token){mutableStateOf<Uri?>(null)}
 var consent by remember(token){mutableStateOf(false)}
 var loading by remember(token){mutableStateOf(true)}
 var busy by remember(token){mutableStateOf(false)}
 var error by remember(token){mutableStateOf("")}
 var refresh by remember(token){mutableIntStateOf(0)}
 var confirm by remember(token){mutableStateOf("")}
 val scope=rememberCoroutineScope();val context=LocalContext.current
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){selected=it}
 BackHandler{if(!busy)onBack()}
 LaunchedEffect(token,refresh){loading=true;val r=withContext(Dispatchers.IO){identityJson(token,"sera_identity_status")};state=r;history=withContext(Dispatchers.IO){spaceRows(spaceCall(token,"sera_identity_history").first)};if(r==null)error="Verification is temporarily unavailable.";if(r?.optString("status")=="draft")type=r.optString("document_type","national_id");loading=false}
 val status=state?.optString("status","none")?:"none"
 val intake=state?.optBoolean("intake_available")==true
 fun run(intent:String){if(busy)return;val id=state?.optString("id").orEmpty();val path=historicalEvidence?:state?.optString("path").orEmpty();busy=true;error="";scope.launch{val result=withContext(Dispatchers.IO){
  when(intent){"submit"->if(spaceCall(token,"sera_identity_submit",JSONObject().put("p_id",id)).first!=null)"" else "Upload evidence before submitting."
   "withdraw"->if(spaceCall(token,"sera_identity_withdraw",JSONObject().put("p_id",id)).first==null)"Unable to withdraw." else if(!evidenceCall(token,path))"Withdrawn. You can retry removing the document." else ""
   else->if(evidenceCall(token,path))"" else "Unable to remove document."}
 };busy=false;error=result;selected=null;historicalEvidence=null;refresh++}}
 Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  TextButton(onClick=onBack,enabled=!busy){Text("Back to settings")}
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){SeraIdentityBadge(Modifier.size(24.dp));Text("Verify your identity",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
  Text("Build trust with the people you work with.")
  if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
  if(!loading&&state==null)OutlinedButton(onClick={refresh++}){Text("Retry")}
  if(state!=null){
   ElevatedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("Verification status",style=MaterialTheme.typography.titleMedium)
    Text(when(status){"pending"->"Under review";"approved"->"Identity verified";"draft"->"Draft";"rejected"->"Not approved";"withdrawn"->"Withdrawn";"revoked"->"Revoked";"expired"->"Reverification required";else->"Not verified"},fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
    Text("Professional name: "+state?.optString("profile_name").orEmpty())
    state?.optString("reason")?.takeUnless{it.isBlank()||it=="null"}?.let{Text(it)}
    if(status=="approved")Text("Identity verified until "+state?.optString("expires_at").orEmpty().take(10)+". This badge does not confirm qualifications or employer legitimacy.",style=MaterialTheme.typography.bodySmall)
   }}
   Text("Your document stays private",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
   Text("Only you and authorized reviewers can access evidence during review. Documents are scheduled for removal 30 days after a decision; incomplete requests expire after 30 days. Remove evidence earlier after a decision or withdraw an active request.",style=MaterialTheme.typography.bodyMedium)
   if(status in listOf("draft","pending"))OutlinedButton(onClick={confirm="withdraw"},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Withdraw request")}
   if(status=="approved")OutlinedButton(onClick={confirm="withdraw"},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Remove verification")}
   if(intake&&status=="draft")OutlinedButton(onClick={run("submit")},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Submit uploaded draft")}
   if(state?.optBoolean("evidence_present")==true&&status in listOf("approved","expired","rejected","withdrawn","revoked"))OutlinedButton(onClick={confirm="delete"},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Remove document")}
   if(!intake&&status in listOf("none","draft","rejected","withdrawn","revoked","expired"))Text("Verification requests are temporarily paused until an authorized reviewer is available. No document is needed right now.",style=MaterialTheme.typography.bodyMedium)
   if(intake&&status in listOf("none","draft","rejected","withdrawn","revoked","expired")){
    Text("Document type",style=MaterialTheme.typography.titleMedium)
    listOf("national_id" to "National ID","passport" to "Passport","driver_license" to "Driving licence").forEach{(key,label)->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=type==key,onClick={type=key},enabled=!busy&&status!="draft");Text(label)}}
    OutlinedButton(onClick={picker.launch(arrayOf("image/jpeg","image/png","application/pdf"))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(selected==null)"Choose document" else "Document selected · change")}
    Text("JPG, PNG or PDF · up to 3 MB. Hide unrelated information where possible; retain your name, photo and authenticity details.",style=MaterialTheme.typography.bodySmall)
    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=consent,onCheckedChange={consent=it},enabled=!busy);Text("I consent to authorized SERA reviewers checking this document for identity verification.",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)}
    Button(onClick={val uri=selected?:return@Button;busy=true;error="";scope.launch{val result=withContext(Dispatchers.IO){submitNativeIdentity(context,token,type,uri)};busy=false;error=result;if(result.isBlank()){selected=null;consent=false};refresh++}},enabled=!busy&&!loading&&selected!=null&&consent,modifier=Modifier.fillMaxWidth()){Text(if(busy)"Submitting securely…" else "Submit for verification")}
   }
   if(history.any{it.optString("id")!=state?.optString("id")}){Text("Previous requests",style=MaterialTheme.typography.titleMedium);history.filter{it.optString("id")!=state?.optString("id")}.forEach{r->Text(r.optString("document_type").replace("_"," ")+" · "+r.optString("status"));if(r.optBoolean("evidence_present"))TextButton(onClick={historicalEvidence=r.optString("path");confirm="delete"},enabled=!busy){Text("Remove previous document")}}}
   TextButton(onClick={refresh++},enabled=!busy){Text("Refresh status")}
  }
 }
 if(confirm.isNotBlank())SeraConfirmAction(if(confirm=="withdraw")"Remove verification or withdraw request?" else "Remove identity document?",if(confirm=="withdraw")"This ends the current request or removes your verified badge, and removes the submitted document." else "Your evidence will be removed. An approved badge remains valid until expiry or revocation.","Confirm","Cancel",destructive=true,onCancel={confirm="";historicalEvidence=null},onConfirm={val action=confirm;confirm="";run(action)})
}
