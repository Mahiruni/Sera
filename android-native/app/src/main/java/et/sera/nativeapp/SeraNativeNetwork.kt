package et.sera.nativeapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

private data class SeraProfessional(
    val id: String, val name: String, val headline: String, val location: String, val avatarPath:String?=null,val matchedSkills:String=""
)

private fun loadProfessionals(token:String,relatedIds:List<String> = emptyList()):Pair<List<SeraProfessional>?,String>{
 if(BuildConfig.SUPABASE_URL.isBlank()||BuildConfig.SUPABASE_ANON_KEY.isBlank())return null to "Professional directory is not configured."
 return try{
  val recs=spaceRows(spaceCall(token,"sera_recommend_talent").first).associateBy{it.optString("member_id")}
  val authors=runCatching{val a=searchRows(token,"posts",mapOf("select" to "author_id","status" to "eq.published","audience" to "eq.public","deleted_at" to "is.null","order" to "created_at.desc","limit" to "150"));(0 until a.length()).map{a.getJSONObject(it).optString("author_id")}}.getOrDefault(emptyList())
  val ids=(relatedIds.distinct().take(500)+recs.keys+authors).filter{it.isNotBlank()}.distinct()
  val people=mutableListOf<SeraProfessional>()
  for(chunk in ids.chunked(100)){
   val cardResult=spaceCall(token,"member_cards",org.json.JSONObject().put("ids",JSONArray(chunk)))
   if(cardResult.first==null)return null to cardResult.second
   val headlines=spaceRows(spaceCall(token,"sera_feed_headlines",org.json.JSONObject().put("p_ids",JSONArray(chunk))).first).associate{it.optString("id") to it.optString("headline").takeUnless{v->v=="null"}.orEmpty()}
   spaceRows(cardResult.first).forEach{p->
    val id=p.optString("id");val name=p.optString("full_name").takeUnless{it=="null"}.orEmpty();val rec=recs[id]
    if(id.isNotBlank()&&name.isNotBlank())people.add(SeraProfessional(id,name,rec?.optString("headline")?.takeUnless{it.isBlank()||it=="null"}?:headlines[id].orEmpty(),"",p.optString("avatar_path").takeUnless{it.isBlank()||it=="null"},rec?.optString("matched_skills")?.takeUnless{it=="null"}.orEmpty()))
   }
  }
  people to ""
 }catch(_:Exception){null to "Unable to load your professional network. Please retry."}
}

@Composable
fun SeraNativeNetwork(token: String, userId: String, modifier: Modifier = Modifier, initialSearch: Boolean = false) {
    var showHiring by remember { mutableStateOf(false) }
    if(showHiring){SeraEmployerDashboard(token,userId,modifier,onBack={showHiring=false});return}
    var showOpportunities by remember { mutableStateOf(false) }
    if(showOpportunities){SeraOpportunities(token,userId,modifier,onBack={showOpportunities=false});return}
    var showEvents by remember { mutableStateOf(false) }
    if(showEvents){SeraEvents(token,userId,modifier,onBack={showEvents=false});return}
    var showCollaboration by remember { mutableStateOf(false) }
    if(showCollaboration){SeraCollaboration(token,userId,modifier,onBack={showCollaboration=false});return}
    var showTalent by remember { mutableStateOf(initialSearch) }
    if(showTalent){SeraTalent(token,userId,modifier,onBack={showTalent=false});return}
    var showCommunities by remember { mutableStateOf(false) }
    if(showCommunities){SeraCommunities(token,modifier,onBack={showCommunities=false});return}
    var people by remember(userId) { mutableStateOf<List<SeraProfessional>>(emptyList()) }
    var query by remember(userId) { mutableStateOf("") }
    var view by remember(userId) { mutableStateOf("Discover") }
    var selected by remember { mutableStateOf<SearchHit?>(null) }
    var more by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var relations by remember(userId) { mutableStateOf(SeraNetworkRelations(emptySet(), emptyList())) }
    var actionPeer by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun perform(peerId: String, operation: suspend () -> String) {
        if (actionPeer != null) return
        actionPeer = peerId
        actionError = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) { operation() }
            if (result.isNotBlank()) actionError = result else reload++
            actionPeer = null
        }
    }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(token,userId,reload) {
        loading = true
        if (userId.isNotBlank()) {
            val relationships = withContext(Dispatchers.IO) { loadSeraRelations(token, userId) }
            relationships.first?.let { relations = it }
            if (relationships.second.isNotBlank()) actionError = relationships.second
        }
        val related=relations.follows.toList()+relations.followers.toList()+relations.requests.map{if(it.requester==userId)it.recipient else it.requester}
        val result=withContext(Dispatchers.IO){loadProfessionals(token,related)};result.first?.let{people=it};error=result.second
        loading = false
    }
    val connected=relations.requests.filter{it.status=="accepted"}.map{if(it.requester==userId)it.recipient else it.requester}.toSet()
    val invitations=relations.requests.filter{it.status=="pending"}
    val incoming=invitations.filter{it.recipient==userId}
    val outgoing=invitations.filter{it.requester==userId}
    selected?.let{hit->SearchResultDetail(token,userId,hit,modifier){selected=null;reload++};return}
    val matches=people.filter{it.id!=userId&&(query.isBlank()||listOf(it.name,it.headline,it.location,it.matchedSkills).any{v->v.contains(query.trim(),ignoreCase=true)})}.filter{when(view){"Connections"->it.id in connected;"Invitations"->invitations.any{r->r.requester==it.id||r.recipient==it.id};"Following"->it.id in relations.follows;"Followers"->it.id in relations.followers;else->it.id !in connected&&it.id !in relations.follows&&invitations.none{r->r.requester==it.id||r.recipient==it.id}}}.let{if(view=="Discover")it.sortedByDescending{p->p.matchedSkills.isNotBlank()} else it}
    val teal=MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Network",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Your professional circle.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Box{TextButton(onClick={more=true}){Text("Explore")};DropdownMenu(expanded=more,onDismissRequest={more=false}){listOf("Hiring workspace" to {showHiring=true},"Collaboration opportunities" to {showOpportunities=true},"Professional events" to {showEvents=true},"Collaboration spaces" to {showCollaboration=true},"Discover portfolio talent" to {showTalent=true},"Explore communities" to {showCommunities=true}).forEach{(label,action)->DropdownMenuItem(text={Text(label)},onClick={more=false;action()})}}}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){listOf("Connections" to connected.size,"Following" to relations.follows.size,"Followers" to relations.followers.size).forEach{(label,count)->Column(Modifier.weight(1f).clickable{view=label;query=""}.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(count.toString(),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Discover","Connections","Invitations","Following","Followers").forEach{v->FilterChip(selected=view==v,onClick={view=v;query=""},label={Text(if(v=="Invitations"&&incoming.isNotEmpty())"$v · ${incoming.size}" else v)})}}
        OutlinedTextField(value=query,onValueChange={query=it.take(100)},label={Text("Search "+view.lowercase())},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(view,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));TextButton(onClick={reload++},enabled=!loading){Text("Refresh")}}
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank()||actionError.isNotBlank())Text(error.ifBlank{actionError},color=MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=16.dp)){
            items(matches,key={it.id}){person->
                val followed=person.id in relations.follows
                val request=invitations.firstOrNull{it.requester==person.id||it.recipient==person.id}
                Column(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        val avatar by produceState<String?>(null,token,person.avatarPath){value=person.avatarPath?.let{path->withContext(Dispatchers.IO){signedPostImage(token,path,"sera-account-private")}}}
                        if(avatar!=null)SeraMediaImage(avatar!!,"Professional photo",128,128,Modifier.size(48.dp).clip(CircleShape)) else Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer,CircleShape),contentAlignment=Alignment.Center){Text(person.name.take(1),color=teal,fontWeight=FontWeight.Bold)}
                        Column(Modifier.weight(1f).clickable{selected=SearchHit(person.id,"People",person.name,person.headline)},verticalArrangement=Arrangement.spacedBy(3.dp)){Text(person.name,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);if(person.headline.isNotBlank())Text(person.headline,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis);if(view=="Discover"&&person.matchedSkills.isNotBlank())Text("Shared skills: "+person.matchedSkills,style=MaterialTheme.typography.labelSmall,color=teal,maxLines=2,overflow=TextOverflow.Ellipsis)}
                        if(person.id in connected)TextButton(onClick={selected=SearchHit(person.id,"People",person.name,person.headline)}){Text("View")}else if(request==null)OutlinedButton(onClick={perform(person.id){requestSeraConnection(token,userId,person.id)}},enabled=actionPeer==null){Text("Connect")}
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        if(request!=null&&request.recipient==userId){Button(onClick={perform(request.id){resolveSeraConnection(token,request,userId,"accepted")}},enabled=actionPeer==null){Text("Accept")};TextButton(onClick={perform(request.id){resolveSeraConnection(token,request,userId,"declined")}},enabled=actionPeer==null){Text("Decline")}}
                        else if(request!=null&&request.requester==userId)TextButton(onClick={perform(request.id){resolveSeraConnection(token,request,userId,"withdrawn")}},enabled=actionPeer==null){Text("Withdraw invitation")}
                        else TextButton(onClick={perform(person.id){changeSeraFollow(token,userId,person.id,!followed)}},enabled=actionPeer==null){Text(if(followed)"Unfollow" else "Follow")}
                    }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant,thickness=.7.dp)
            }
            if(matches.isEmpty()&&!loading&&error.isBlank())item{Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(if(view=="Invitations")"You're all caught up" else "No professionals here yet",style=MaterialTheme.typography.titleMedium);TextButton(onClick={view="Discover";query=""}){Text("Discover people")}}}
        }
    }
}
