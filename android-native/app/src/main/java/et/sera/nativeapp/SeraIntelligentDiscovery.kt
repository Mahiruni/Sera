package et.sera.nativeapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

internal data class DiscoveryRecommendation(val id:String,val kind:String,val title:String,val subtitle:String,val reason:String,val score:Int,val body:String="",val status:String="")
private data class DiscoverySnapshot(val rows:List<DiscoveryRecommendation>,val partial:Boolean,val needsProfile:Boolean)
private fun JSONObject.field(key:String)=optString(key).takeUnless{it=="null"}.orEmpty()
private fun terms(raw:String)=raw.split(',', ';', '\n').map{it.trim().lowercase(Locale.ROOT)}.filter{it.length in 2..80}.distinct().take(20)
private fun matches(text:String,term:String)=Regex("(^|[^\\p{L}\\p{N}])"+Regex.escape(term)+"($|[^\\p{L}\\p{N}])",RegexOption.IGNORE_CASE).containsMatchIn(text)
private fun rows(array:org.json.JSONArray)= (0 until array.length()).map{array.getJSONObject(it)}
private fun rpcRows(token:String,name:String,body:JSONObject=JSONObject()):List<JSONObject>{
 val result=spaceCall(token,name,body);if(result.first==null)throw IllegalStateException(result.second)
 return rows(org.json.JSONArray(result.first))
}
private suspend fun loadDiscovery(token:String,userId:String):DiscoverySnapshot=coroutineScope {
 val p=runCatching{searchRows(token,"profiles",mapOf("select" to "skills,preferred_roles,location","id" to "eq."+userId,"limit" to "1")).optJSONObject(0)}
 val profile=p.getOrNull()
 if(profile==null)return@coroutineScope DiscoverySnapshot(emptyList(),true,false)
 val skills=terms(profile.field("skills"));val roles=terms(profile.field("preferred_roles"));val all=(skills+roles).distinct()
 if(all.isEmpty())return@coroutineScope DiscoverySnapshot(emptyList(),false,true)
 val location=profile.field("location").trim()
 fun reason(values:List<String>)=values.distinct().take(4).joinToString(", ")
 fun ranked(input:List<DiscoveryRecommendation>,limit:Int=5)=input.filter{it.score>0&&it.id.isNotBlank()}.distinctBy{it.id}.sortedWith(compareByDescending<DiscoveryRecommendation>{it.score}.thenBy{it.id}).take(limit)
 val jobs=async(Dispatchers.IO){runCatching{
  ranked(rows(searchRows(token,"jobs",mapOf("select" to "id,title,company,location,work_type,description,requirements","status" to "eq.open","order" to "created_at.desc,id.asc","limit" to "100"))).map{r->
   val title=r.field("title");val text=listOf(title,r.field("description"),r.field("requirements")).joinToString(" ");val s=skills.filter{matches(text,it)};val role=roles.filter{matches(title,it)};val local=location.isNotBlank()&&r.field("location").contains(location,true)
   DiscoveryRecommendation(r.field("id"),"Jobs",title,listOf(r.field("company"),r.field("location"),r.field("work_type")).filter{it.isNotBlank()}.joinToString(" · "),reason(role+s),if(s.isEmpty()&&role.isEmpty())0 else s.size*3+role.size*6+if(local)1 else 0)
  })
 }}
 val people=async(Dispatchers.IO){runCatching{
  val relations=loadSeraRelations(token,userId).first?:throw IllegalStateException("Relationships unavailable")
  val excluded=relations.follows+relations.requests.map{if(it.requester==userId)it.recipient else it.requester}+userId
  ranked(rpcRows(token,"sera_recommend_talent").filter{it.field("member_id") !in excluded}.map{r->val s=terms(r.field("matched_skills"));DiscoveryRecommendation(r.field("member_id"),"People",r.field("full_name"),r.field("headline"),reason(s),s.size,r.field("showcase_title"))})
 }}
 val projects=async(Dispatchers.IO){runCatching{
  ranked(rows(searchRows(token,"posts",mapOf("select" to "id,author_id,title,body,category,skills,published_at","status" to "eq.published","kind" to "eq.project","audience" to "eq.public","deleted_at" to "is.null","repost_of" to "is.null","author_id" to "neq."+userId,"order" to "published_at.desc,id.asc","limit" to "100"))).map{r->
   val tags=r.optJSONArray("skills");val tagSet=if(tags==null)emptySet() else (0 until tags.length()).map{tags.optString(it).trim().lowercase(Locale.ROOT)}.toSet()
   val title=r.field("title");val category=r.field("category");val text=title+" "+category+" "+r.field("body");val exact=skills.filter{it in tagSet};val s=skills.filter{it in tagSet||matches(text,it)};val role=roles.filter{matches(title+" "+category,it)}
   DiscoveryRecommendation(r.field("id"),"Projects",title,category,reason(s+role),exact.size*5+(s.size-exact.size)*2+role.size*3,r.field("body").take(160))
  })
 }}
 val communities=async(Dispatchers.IO){runCatching{
  ranked(rpcRows(token,"sera_community_directory",JSONObject().put("p_query","").put("p_topic","")).filter{!it.optBoolean("joined")&&!it.optBoolean("is_owner")}.map{r->
   val text=r.field("name")+" "+r.field("description")+" "+r.field("topic");val s=skills.filter{matches(text,it)};val role=roles.filter{matches(text,it)}
   DiscoveryRecommendation(r.field("id"),"Communities",r.field("name"),r.field("topic")+" · "+r.optInt("member_count")+" members",reason(s+role),s.size*3+role.size*2,r.field("description"))
  })
 }}
 val spaces=async(Dispatchers.IO){runCatching{
  ranked(rpcRows(token,"sera_list_spaces").filter{it.field("status") in listOf("active","invited")}.map{r->val s=all.filter{matches(r.field("name")+" "+r.field("description"),it)};val status=r.field("status");DiscoveryRecommendation(r.field("id"),"Spaces",r.field("name"),if(status=="invited")"Pending invitation" else "Your team",reason(s),s.size,r.field("description"),status)},3)
 }}
 val sections=listOf(jobs.await(),people.await(),projects.await(),communities.await(),spaces.await())
 DiscoverySnapshot(sections.flatMap{it.getOrDefault(emptyList())},sections.any{it.isFailure},false)
}

@Composable internal fun SeraIntelligentDiscovery(token:String,userId:String,modifier:Modifier=Modifier,onBack:()->Unit){
 var snapshot by remember(token,userId){mutableStateOf<DiscoverySnapshot?>(null)}
 var loading by remember(token,userId){mutableStateOf(true)}
 var selected by remember(token,userId){mutableStateOf<DiscoveryRecommendation?>(null)}
 var thread by remember(token,userId){mutableStateOf<String?>(null)}
 var reload by remember{mutableIntStateOf(0)}
 var busy by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 val scope=rememberCoroutineScope()
 fun closeDetail(){selected=null;thread=null;reload++}
 BackHandler{if(selected!=null)closeDetail() else onBack()}
 LaunchedEffect(token,userId,reload){loading=true;snapshot=null;snapshot=loadDiscovery(token,userId);loading=false}
 selected?.let{r->
  when(r.kind){
   "Jobs"->{SearchResultDetail(token,userId,SearchHit(r.id,"Jobs",r.title,r.subtitle),modifier){closeDetail()};return}
   "People"->{if(thread!=null)SeraNativeInbox(token,userId,modifier,initialThread=thread,initialPeer=r.id,initialName=r.title,onThreadExit={thread=null}) else SeraTalentPortfolio(token,userId,r.id,modifier,onMessage={thread=it}){closeDetail()};return}
   "Communities"->{SeraCommunities(token,modifier,initialQuery=r.title){closeDetail()};return}
   "Spaces"->{SeraCollaboration(token,userId,modifier,initialSpace=if(r.status=="active")r.id else null){closeDetail()};return}
  }
 }
 Column(modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick=onBack){Text("Back")};TextButton(onClick={reload++},enabled=!loading){Text("Refresh")}}
  Text("Discover for you",style=MaterialTheme.typography.headlineMedium)
  Text("Opportunities, people and ideas aligned with your skills.",style=MaterialTheme.typography.bodyMedium)
  if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(snapshot?.partial==true)Text("Some recommendations are unavailable. You can refresh to retry.",color=MaterialTheme.colorScheme.error)
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
  if(snapshot?.needsProfile==true)Text("Add skills or preferred roles to your professional profile to personalize discovery.")
  else if(!loading&&snapshot?.rows.orEmpty().isEmpty())Text("No matching recommendations yet. Explore search or update your profile.")
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=20.dp)){
   listOf("Jobs","People","Projects","Communities","Spaces").forEach{kind->
    val matches=snapshot?.rows.orEmpty().filter{it.kind==kind}
    if(matches.isNotEmpty()){
     item(key="heading"+kind){Text(when(kind){"Jobs"->"Jobs for you";"People"->"People to connect with";"Projects"->"Projects for you";"Communities"->"Communities for you";else->"Your relevant collaboration spaces"},style=MaterialTheme.typography.titleLarge)}
     items(matches,key={it.kind+it.id}){r->
      ElevatedCard(onClick={selected=r},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){
       Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text(r.title,style=MaterialTheme.typography.titleMedium)
        Text(r.subtitle,style=MaterialTheme.typography.bodySmall)
        if(r.body.isNotBlank())Text(r.body.take(160),maxLines=3)
        Text("Matches: "+r.reason,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
        if(r.kind=="Communities")TextButton(enabled=!busy,onClick={busy=true;error="";scope.launch{val result=withContext(Dispatchers.IO){spaceCall(token,"sera_join_community",JSONObject().put("p_id",r.id).put("p_join",true))};busy=false;if(result.first!=null)reload++ else error=result.second}}){Text("Join community")}
        else Text("View "+when(r.kind){"Jobs"->"opportunity";"People"->"portfolio";"Projects"->"project";else->"space"},color=MaterialTheme.colorScheme.primary)
       }
      }
     }
    }
   }
  }
 }
 selected?.takeIf{it.kind=="Projects"}?.let{r->SeraNativePostDetail(token,r.id){closeDetail()}}
}
