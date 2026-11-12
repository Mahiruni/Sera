package et.sera.nativeapp

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

private data class SearchHit(val id:String,val kind:String,val title:String,val subtitle:String,val body:String="")
private val searchTabs=listOf("All","People","Jobs","Posts","Projects","Companies","Topics")
private fun cleanSearch(s:String)=s.take(80).replace(Regex("[^\\p{L}\\p{N}\\s-]")," ").replace(Regex("\\s+")," ").trim()
private fun searchRows(token:String,table:String,params:Map<String,String>):JSONArray {
    val query=params.entries.joinToString("&"){Uri.encode(it.key)+"="+Uri.encode(it.value)}
    val connection=(URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/"+table+"?"+query).openConnection() as HttpURLConnection)
    try {
        connection.connectTimeout=10000;connection.readTimeout=12000
        connection.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization","Bearer "+token)
        connection.setRequestProperty("Cache-Control","no-store")
        if(connection.responseCode !in 200..299) throw IllegalStateException("Search unavailable")
        return JSONArray(connection.inputStream.bufferedReader().use{it.readText()})
    } finally {connection.disconnect()}
}
private fun readSearch(token:String,query:String,tab:String,location:String,work:String,skill:String,days:String):Pair<List<SearchHit>,Boolean>{
    val q=cleanSearch(query);val loc=cleanSearch(location);val tag=cleanSearch(skill)
    val hits=mutableListOf<SearchHit>();var failed=false
    val since=days.toLongOrNull()?.takeIf{it in listOf(7L,30L,90L)}?.let{java.time.Instant.now().minusSeconds(it*86400).toString()}
    fun include(kind:String)=tab=="All"||tab==kind
    fun section(block:()->Unit){try{block()}catch(_:Exception){failed=true}}
    fun params(select:String,order:String)=mutableMapOf("select" to select,"order" to order,"limit" to if(tab=="All")"12" else "40")
    fun value(row:org.json.JSONObject,key:String)=row.optString(key).takeUnless{it=="null"}.orEmpty()
    fun rows(data:JSONArray,block:(org.json.JSONObject)->Unit){for(i in 0 until data.length())block(data.getJSONObject(i))}
    if(include("People")) section{
        val p=params("id,full_name,headline,location","full_name.asc,id.asc")
        if(q.isNotEmpty())p["or"]="(full_name.ilike.%$q%,headline.ilike.%$q%,location.ilike.%$q%)"
        if(loc.isNotEmpty())p["location"]="ilike.%$loc%"
        rows(searchRows(token,"post_authors",p)){r->hits.add(SearchHit(value(r,"id"),"People",value(r,"full_name"),listOf(value(r,"headline"),value(r,"location")).filter{it.isNotBlank()}.joinToString(" · ")))}
    }
    if(include("Jobs")||include("Companies")) section{
        val p=params("id,title,company,location,work_type,description,requirements","created_at.desc,id.desc")
        p["status"]="eq.open"
        if(q.isNotEmpty())p["or"]="(title.ilike.%$q%,company.ilike.%$q%,location.ilike.%$q%)"
        if(loc.isNotEmpty())p["location"]="ilike.%$loc%"
        if(work in listOf("Full-time","Part-time","Contract","Internship"))p["work_type"]="eq.$work"
        since?.let{p["created_at"]="gte.$it"}
        rows(searchRows(token,"jobs",p)){r->
            val id=value(r,"id");val company=value(r,"company");val subtitle=listOf(company,value(r,"location"),value(r,"work_type")).filter{it.isNotBlank()}.joinToString(" · ")
            if(include("Jobs"))hits.add(SearchHit(id,"Jobs",value(r,"title"),subtitle,value(r,"description")+"\n\n"+value(r,"requirements")))
            if(include("Companies")&&hits.none{it.kind=="Companies"&&it.title.equals(company,true)})hits.add(SearchHit(id,"Companies",company,value(r,"location"),value(r,"description")))
        }
    }
    if(include("Posts")||include("Projects")||include("Topics"))section{
        val p=params("id,title,body,kind,category,skills,location","published_at.desc,id.desc")
        p["status"]="eq.published";p["deleted_at"]="is.null"
        if(tab=="Projects")p["kind"]="eq.project"
        if(tab=="Posts")p["kind"]="neq.project"
        if(q.isNotEmpty()&&tab!="Topics")p["or"]="(title.ilike.%$q%,body.ilike.%$q%,category.ilike.%$q%,skills.cs.{$q})"
        if(loc.isNotEmpty()&&tab!="Topics")p["location"]="ilike.%$loc%"
        if(tag.isNotEmpty())p["skills"]="cs.{$tag}"
        since?.let{p["published_at"]="gte.$it"}
        if(tab=="Topics")p["limit"]="100"
        rows(searchRows(token,"posts",p)){r->
            val kind=if(value(r,"kind")=="project")"Projects" else "Posts"
            val title=value(r,"title").ifBlank{value(r,"body").take(90)}.ifBlank{"Post"}
            if(include(kind))hits.add(SearchHit(value(r,"id"),kind,title,value(r,"category"),value(r,"body")))
            if(include("Topics")){
                val tags=mutableListOf(value(r,"category"));val arr=r.optJSONArray("skills")
                if(arr!=null)for(i in 0 until arr.length())tags.add(arr.optString(i))
                tags.filter{it.isNotBlank()&&(q.isBlank()||it.contains(q,true))}.forEach{t->if(hits.none{it.kind=="Topics"&&it.title.equals(t,true)})hits.add(SearchHit(t,"Topics",t,"Professional topic"))}
            }
        }
    }
    val ranked=hits.filter{it.title.isNotBlank()}.sortedBy{when{it.title.equals(q,true)->0;it.title.startsWith(q,true)->1;it.title.contains(q,true)->2;else->3}}
    return (if(tab=="All")searchTabs.drop(1).flatMap{k->ranked.filter{it.kind==k}.take(3)}else ranked) to failed
}

@Composable
internal fun SeraUniversalSearch(token:String,userId:String,modifier:Modifier=Modifier){
    var query by remember(userId){mutableStateOf("")}
    var tab by remember(userId){mutableStateOf("All")}
    var location by remember(userId){mutableStateOf("")}
    var work by remember(userId){mutableStateOf("")}
    var skill by remember(userId){mutableStateOf("")}
    var days by remember(userId){mutableStateOf("")}
    var filters by remember{mutableStateOf(false)}
    var history by remember(userId){mutableStateOf<List<String>>(emptyList())}
    var results by remember(userId){mutableStateOf<List<SearchHit>>(emptyList())}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf(false)}
    var selected by remember(userId){mutableStateOf<SearchHit?>(null)}
    var retry by remember{mutableIntStateOf(0)}
    val focus=LocalFocusManager.current
    fun submit(term:String){val q=cleanSearch(term);query=q;if(q.isNotBlank())history=(listOf(q)+history.filterNot{it.equals(q,true)}).take(8);focus.clearFocus();retry++}
    LaunchedEffect(token,userId,query,tab,location,work,skill,days,retry){
        results=emptyList();error=false
        if(cleanSearch(query).length<2&&listOf(location,work,skill,days).all{it.isBlank()}){loading=false;return@LaunchedEffect}
        loading=true;delay(250)
        val response=withContext(Dispatchers.IO){readSearch(token,query,tab,location,work,skill,days)}
        results=response.first;error=response.second;loading=false
    }
    Column(modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Search SERA",fontSize=25.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(value=query,onValueChange={query=it.take(80)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(18.dp),label={Text("People, jobs, posts, skills…")},leadingIcon={Icon(Icons.Outlined.Search,null)},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit(query)}),trailingIcon={TextButton(onClick={submit(query)}){Text("Go")}})
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){searchTabs.forEach{k->FilterChip(selected=tab==k,onClick={tab=k},label={Text(k)})}}
        TextButton(onClick={filters=!filters}){Text(if(filters)"Hide filters" else "Filters")}
        if(filters)Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
            OutlinedTextField(location,{location=it.take(80)},label={Text("Location")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(skill,{skill=it.take(80)},label={Text("Exact skill tag")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("","Full-time","Part-time","Contract","Internship").forEach{v->FilterChip(selected=work==v,onClick={work=v},label={Text(v.ifBlank{"Any work type"})})}}
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("","7","30","90").forEach{v->FilterChip(selected=days==v,onClick={days=v},label={Text(if(v.isBlank())"Any time" else "$v days")})}}
            TextButton(onClick={location="";skill="";work="";days=""}){Text("Clear filters")}
        }
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error)Row{Text("Some results could not load.",Modifier.weight(1f));TextButton(onClick={retry++}){Text("Retry")}}
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)){
            if(query.isBlank()&&history.isNotEmpty()) {
                item{Row{Text("Recent searches",Modifier.weight(1f),fontWeight=FontWeight.Bold);TextButton(onClick={history=emptyList()}){Text("Clear all")}}}
                items(history,key={"recent"+it}){term->Row{TextButton(onClick={submit(term)},modifier=Modifier.weight(1f)){Text(term)};TextButton(onClick={history=history.filterNot{it==term}}){Text("Remove")}}}
            }
            if(!loading&&results.isEmpty()&&query.length>=2)item{Text(if(error)"Try again." else "No matching results.",modifier=Modifier.padding(12.dp))}
            items(results,key={it.kind+it.id}){hit->
                ElevatedCard(onClick={if(hit.kind=="Topics"){submit(hit.title);tab="Posts"}else selected=hit},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(hit.kind,color=MaterialTheme.colorScheme.primary,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                        Text(hit.title,fontWeight=FontWeight.Bold,fontSize=17.sp)
                        if(hit.subtitle.isNotBlank())Text(hit.subtitle,fontSize=13.sp)
                        if(hit.body.isNotBlank())Text(hit.body.take(140),maxLines=2,fontSize=13.sp)
                    }
                }
            }
        }
    }
    selected?.let{hit->
        if(hit.kind=="Posts"||hit.kind=="Projects")SeraNativePostDetail(token,hit.id){selected=null}
        else AlertDialog(onDismissRequest={selected=null},title={Text(hit.title)},text={Column{Text(hit.subtitle);if(hit.body.isNotBlank())Text(hit.body.take(4000))}},confirmButton={TextButton(onClick={selected=null}){Text("Close")}})
    }
}
