package et.sera.nativeapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
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
    val id: String, val name: String, val headline: String, val location: String
)

private fun loadProfessionals(token: String): Pair<List<SeraProfessional>?, String> {
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
        }.distinct().take(80)
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
                "", "")
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        people to ""
    } catch (_: Exception) {
        null to "Unable to connect. Please retry."
    } finally {
        conn?.disconnect()
    }
}

@Composable
fun SeraNativeNetwork(token: String, userId: String, modifier: Modifier = Modifier) {
    var showOpportunities by remember { mutableStateOf(false) }
    if(showOpportunities){SeraOpportunities(token,userId,modifier,onBack={showOpportunities=false});return}
    var showEvents by remember { mutableStateOf(false) }
    if(showEvents){SeraEvents(token,userId,modifier,onBack={showEvents=false});return}
    var showCollaboration by remember { mutableStateOf(false) }
    if(showCollaboration){SeraCollaboration(token,userId,modifier,onBack={showCollaboration=false});return}
    var showTalent by remember { mutableStateOf(false) }
    if(showTalent){SeraTalent(token,userId,modifier,onBack={showTalent=false});return}
    var showCommunities by remember { mutableStateOf(false) }
    if(showCommunities){SeraCommunities(token,modifier,onBack={showCommunities=false});return}
    var people by remember { mutableStateOf<List<SeraProfessional>>(emptyList()) }
    var query by remember { mutableStateOf("") }
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
        val result = withContext(Dispatchers.IO) { loadProfessionals(token) }
        result.first?.let { people = it }
        error = result.second
        if (userId.isNotBlank()) {
            val relationships = withContext(Dispatchers.IO) { loadSeraRelations(token, userId) }
            relationships.first?.let { relations = it }
            if (relationships.second.isNotBlank()) actionError = relationships.second
        }
        loading = false
    }
    val matches = remember(people, query, userId) {
        people.filter { it.id != userId && (query.isBlank() ||
            listOf(it.name, it.headline, it.location).any { value -> value.contains(query.trim(), ignoreCase = true) }) }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Your professional network", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color(0xFF183B40))
        Text("Discover people, skills and ideas.", fontSize = 13.sp, color = Color(0xFF657A7E))
        FilledTonalButton(onClick={showOpportunities=true},modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("Collaboration opportunities")}
        FilledTonalButton(onClick={showEvents=true},modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("Professional events")}
        FilledTonalButton(onClick={showCollaboration=true},modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("Collaboration spaces")}
        FilledTonalButton(onClick={showTalent=true},modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("Discover portfolio talent")}
        FilledTonalButton(onClick={showCommunities=true},modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("Explore communities")}
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query, onValueChange = { if (it.length <= 100) query = it },
            label = { Text("Search professionals") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${matches.size} professionals", color = Color(0xFF087E83), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { reload++ }, enabled = !loading) { Text("Refresh") }
        }
        if (loading && people.isEmpty()) SeraLoadingCards(count = 3, modifier = Modifier.padding(vertical = 12.dp))
        else if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (actionError.isNotBlank()) Text(actionError, color = MaterialTheme.colorScheme.error)
        val pendingReceived = relations.requests.filter { it.recipient == userId && it.status == "pending" }
        AnimatedVisibility(pendingReceived.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connection invitations · ${pendingReceived.size}", color = Color(0xFF087E83),
                    fontWeight = FontWeight.Bold)
                pendingReceived.forEach { request ->
                    val senderName = people.firstOrNull { it.id == request.requester }?.name ?: "Professional"
                    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(senderName, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { perform(request.id) {
                                    resolveSeraConnection(token, request, userId, "accepted")
                                } }, enabled = actionPeer == null) { Text("Accept") }
                                OutlinedButton(onClick = { perform(request.id) {
                                    resolveSeraConnection(token, request, userId, "declined")
                                } }, enabled = actionPeer == null) { Text("Decline") }
                            }
                        }
                    }
                }
            }
        }
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { reload++ }) { Text("Retry") }
        }
        if (!loading && error.isBlank() && matches.isEmpty()) {
            Text("No matching professionals. Try another search.", modifier = Modifier.padding(top = 24.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
            items(matches, key = { it.id }) { person ->
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFDFF4F1)),
                            contentAlignment = Alignment.Center) {
                            Text(person.name.take(1).uppercase(), color = Color(0xFF087E83),
                                fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(person.name, color = Color(0xFF183B40), fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (person.headline.isNotBlank()) Text(person.headline,
                                color = Color(0xFF516D70), fontSize = 13.sp, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            if (person.location.isNotBlank()) Text(person.location,
                                color = Color(0xFF087E83), fontSize = 12.sp)
                        }
                    }
                    val followed = person.id in relations.follows
                    val request = relations.requests.firstOrNull {
                        it.status == "pending" && (
                            (it.requester == userId && it.recipient == person.id) ||
                            (it.recipient == userId && it.requester == person.id))
                    }
                    val connected = relations.requests.any {
                        it.status == "accepted" && (
                            (it.requester == userId && it.recipient == person.id) ||
                            (it.recipient == userId && it.requester == person.id))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp).animateContentSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { perform(person.id) {
                            changeSeraFollow(token, userId, person.id, !followed)
                        } }, enabled = actionPeer == null) { Text(if (followed) "Following" else "Follow") }
                        when {
                            connected -> Text("Connected", modifier = Modifier.padding(12.dp), color = Color(0xFF087E83))
                            request != null && request.requester == userId ->
                                TextButton(onClick = { perform(person.id) {
                                    resolveSeraConnection(token, request, userId, "withdrawn")
                                } }, enabled = actionPeer == null) { Text("Withdraw") }
                            request != null ->
                                Button(onClick = { perform(person.id) {
                                    resolveSeraConnection(token, request, userId, "accepted")
                                } }, enabled = actionPeer == null) { Text("Accept") }
                            else -> Button(onClick = { perform(person.id) {
                                requestSeraConnection(token, userId, person.id)
                            } }, enabled = actionPeer == null) { Text("Connect") }
                        }
                    }
                }
            }
        }
    }
}
