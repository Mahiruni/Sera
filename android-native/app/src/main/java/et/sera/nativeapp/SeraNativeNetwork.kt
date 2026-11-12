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
    val id: String, val name: String, val headline: String, val location: String, val avatar:String?=null
)

private fun loadProfessionals(token: String, relatedIds:List<String> = emptyList()): Pair<List<SeraProfessional>?, String> {
    if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank())
        return null to "Professional directory is not configured."
    var conn: HttpURLConnection? = null
    return try {
        // Profiles are private under RLS. Discover only authors of published
        // posts, then resolve their public display cards through member_cards.
        conn = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') +
            "/rest/v1/posts?select=author_id&status=eq.published&order=created_at.desc&limit=150")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
        }
        if (conn.responseCode !in 200..299)
            return null to "Directory is unavailable (HTTP ${conn.responseCode})."
        val authors = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        val ids = (0 until authors.length()).mapNotNull { i ->
            authors.getJSONObject(i).optString("author_id").takeIf { it.isNotBlank() }
        }.plus(relatedIds).distinct().take(200)
        conn.disconnect()
        conn = null
        if (ids.isEmpty()) return emptyList<SeraProfessional>() to ""
        conn = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/member_cards")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { output ->
            output.write(org.json.JSONObject().put("ids", JSONArray(ids)).toString().toByteArray())
        }
        if (conn.responseCode !in 200..299)
            return null to "Unable to load public member cards (HTTP ${conn.responseCode})."
        val data = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        val people = (0 until data.length()).map { i ->
            val p = data.getJSONObject(i)
            SeraProfessional(p.optString("id"),
                p.optString("full_name").takeUnless { it == "null" }.orEmpty(),
                p.optString("headline").takeUnless{it=="null"}.orEmpty(), "", p.optString("avatar_path").takeUnless{it.isBlank()||it=="null"}?.let{signedPostImage(token,it,"sera-account-private")})
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        people to ""
    } catch (_: Exception) {
        null to "Unable to connect. Please retry."
    } finally {
        conn?.disconnect()
    }
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
    var people by remember { mutableStateOf<List<SeraProfessional>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var view by remember { mutableStateOf("Discover") }
    var selected by remember { mutableStateOf<SearchHit?>(null) }
    var more by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var relations by remember { mutableStateOf(SeraNetworkRelations(emptySet(), emptyList())) }
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
    LaunchedEffect(token, reload) {
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
    val matches=people.filter{it.id!=userId&&(query.isBlank()||listOf(it.name,it.headline,it.location).any{v->v.contains(query.trim(),ignoreCase=true)})}.filter{when(view){"Connections"->it.id in connected;"Invitations"->invitations.any{r->r.requester==it.id||r.recipient==it.id};"Following"->it.id in relations.follows;"Followers"->it.id in relations.followers;else->it.id !in connected&&it.id !in relations.follows&&invitations.none{r->r.requester==it.id||r.recipient==it.id}}}
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
                        if(person.avatar!=null)SeraMediaImage(person.avatar,"Professional photo",128,128,Modifier.size(48.dp).clip(CircleShape)) else Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer,CircleShape),contentAlignment=Alignment.Center){Text(person.name.take(1),color=teal,fontWeight=FontWeight.Bold)}
                        Column(Modifier.weight(1f).clickable{selected=SearchHit(person.id,"People",person.name,person.headline)},verticalArrangement=Arrangement.spacedBy(3.dp)){Text(person.name,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);if(person.headline.isNotBlank())Text(person.headline,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)}
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
