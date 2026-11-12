package et.sera.nativeapp

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

internal data class NativeJob(
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    val workType: String,
    val description: String,
    val requirements: String,
    val createdAt: String
)

private fun fetchOpenJobs(token: String): Pair<List<NativeJob>?, String> {
    if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank())
        return null to "Job service is not configured."
    var connection: HttpURLConnection? = null
    return try {
        val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') +
            "/rest/v1/jobs?select=id,title,company,location,work_type,description,requirements,created_at&status=eq.open&order=created_at.desc&limit=60")
        connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
        }
        val status = connection.responseCode
        if (status !in 200..299) return null to "Unable to load opportunities (HTTP $status)."
        val rows = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        val jobs = (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            NativeJob(
                id = item.optString("id"),
                title = item.optString("title"),
                company = item.optString("company"),
                location = item.optString("location"),
                workType = item.optString("work_type"),
                description = item.optString("description"),
                requirements = item.optString("requirements"),
                createdAt = item.optString("created_at")
            )
        }
        jobs to ""
    } catch (_: Exception) {
        null to "Could not connect. Pull to retry."
    } finally {
        connection?.disconnect()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeraNativeJobs(token: String, userId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var jobs by remember { mutableStateOf<List<NativeJob>>(emptyList()) }
    var selected by remember { mutableStateOf<NativeJob?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var companyChat by remember { mutableStateOf<String?>(null) }
    var contacting by remember { mutableStateOf(false) }
    var contactError by remember { mutableStateOf("") }
    val scope=rememberCoroutineScope()
    if(companyChat!=null){ Box(modifier.fillMaxSize()){SeraCompanyInbox(token,userId,companyChat,onBack={companyChat=null})}; return }
    LaunchedEffect(token, reload) {
        loading = true
        val result = withContext(Dispatchers.IO) { fetchOpenJobs(token) }
        result.first?.let { jobs = it }
        error = result.second
        loading = false
    }
    val filtered = remember(jobs, search) {
        jobs.filter {
            val query = search.trim()
            query.isBlank() || listOf(it.title, it.company, it.location, it.workType)
                .any { field -> field.contains(query, ignoreCase = true) }
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Opportunities", fontSize = 25.sp,
                    fontWeight = FontWeight.Bold, color = Color(0xFF183B40))
                Text("Discover your next chapter.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF657A7E))
            }
            Button(onClick = { showCreate = true },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087E83)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null,
                    modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text("Post job", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { if (it.length <= 100) search = it },
            label = { Text("Search roles, companies, locations") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} opportunities", color = Color(0xFF087E83), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { reload++ }, enabled = !loading) { Text("Refresh") }
        }
        if (loading && jobs.isEmpty()) SeraLoadingCards(count = 3, modifier = Modifier.padding(vertical = 12.dp))
        else if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = { reload++ }) { Text("Retry") }
        }
        if (!loading && error.isBlank() && filtered.isEmpty()) {
            Text("No matching opportunities yet. Try another search.", modifier = Modifier.padding(top = 30.dp))
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filtered, key = { it.id }) { job ->
                ElevatedCard(
                    onClick = { selected = job },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(job.title, color = Color(0xFF183B40), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(job.company.ifBlank { "Employer" }, color = Color(0xFF087E83), fontWeight = FontWeight.Medium)
                        Text(listOf(job.location, job.workType).filter { it.isNotBlank() }.joinToString("  ·  "),
                            color = Color(0xFF64787B), fontSize = 12.sp)
                        if (job.description.isNotBlank())
                            Text(job.description, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color(0xFF183B40))
                        Text("View opportunity  →", color = Color(0xFF087E83), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    if (showCreate) {
        SeraNativeJobComposer(
            token = token,
            userId = userId,
            onDismiss = { showCreate = false },
            onPublished = {
                showCreate = false
                selected = null
                search = ""
                reload++
            }
        )
    }
    selected?.let { job ->
        SeraSignatureSheet(onDismissRequest = { selected = null }) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(job.title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color(0xFF183B40))
                Text(job.company, color = Color(0xFF087E83), fontWeight = FontWeight.SemiBold)
                Text(listOf(job.location, job.workType).filter { it.isNotBlank() }.joinToString(" · "))
                HorizontalDivider()
                Text("About the role", fontWeight = FontWeight.Bold)
                Text(job.description.ifBlank { "No description provided." }, maxLines = 12)
                if (job.requirements.isNotBlank()) {
                    Text("Requirements", fontWeight = FontWeight.Bold)
                    Text(job.requirements, maxLines = 8)
                }
                Text("Listings are submitted by employers. Never pay to apply.", fontSize = 12.sp, color = Color.Gray)
                if(contactError.isNotBlank()) Text(contactError,color=MaterialTheme.colorScheme.error)
                FilledTonalButton(onClick={contacting=true;contactError="";scope.launch{val (id,e)=withContext(Dispatchers.IO){startCompanyJobChat(token,job.id)};contacting=false;if(id!=null){selected=null;companyChat=id}else contactError=e}},enabled=!contacting && userId.isNotBlank(),modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(16.dp)){Text(if(contacting)"Opening conversation…" else "Message company") }
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sera.hisabtech.com/jobs/" + Uri.encode(job.id))))
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087E83))
                ) { Text("View full listing on SERA") }
            }
        }
    }
}
