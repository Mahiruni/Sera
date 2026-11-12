package et.sera.nativeapp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
@Composable internal fun SeraSpaceProjects(token:String,userId:String,spaceId:String,spaceName:String,isOwner:Boolean,modifier:Modifier=Modifier,onBack:()->Unit){
 var projects by remember{mutableStateOf<List<JSONObject>>(emptyList())};var tasks by remember{mutableStateOf<List<JSONObject>>(emptyList())};var members by remember{mutableStateOf<List<JSONObject>>(emptyList())};var selected by remember{mutableStateOf<String?>(null)};var editor by remember{mutableStateOf(false)};var title by remember{mutableStateOf("")};var desc by remember{mutableStateOf("")};var due by remember{mutableStateOf("")};var assignee by remember{mutableStateOf<String?>(null)};var menu by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf("")};var reload by remember{mutableIntStateOf(0)};val scope=rememberCoroutineScope()
 fun act(rpc:String,p:JSONObject){if(busy)return;busy=true;scope.launch{val r=withContext(Dispatchers.IO){spaceCall(token,rpc,p)};busy=false;error=r.second;if(r.first!=null){editor=false;title="";desc="";due="";assignee=null;reload++}}}
 fun back(){if(selected!=null){selected=null;editor=false}else onBack()}
 androidx.activity.compose.BackHandler{back()}
 LaunchedEffect(token,spaceId,reload,selected){loading=true;val r=withContext(Dispatchers.IO){spaceCall(token,"sera_space_projects_list",JSONObject().put("p_space",spaceId))};projects=spaceRows(r.first);error=r.second;val id=selected;if(id!=null){val t=withContext(Dispatchers.IO){spaceCall(token,"sera_project_tasks_list",JSONObject().put("p_project",id))};tasks=spaceRows(t.first);if(t.second.isNotBlank())error=t.second};members=spaceRows(withContext(Dispatchers.IO){spaceCall(token,"sera_space_members",JSONObject().put("p_space",spaceId))}.first).filter{it.optString("status")=="active"};loading=false}
 val current=projects.firstOrNull{it.optString("id")==selected}
 Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 TextButton(onClick={back()}){Text("Back")}
 Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF123A40),Color(0xFF087E83)))).padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
 Text(current?.optString("title")?:spaceName,color=Color.White,style=MaterialTheme.typography.headlineSmall)
 Text(current?.optString("description")?:"Projects · Plan, assign and deliver together",color=Color(0xFFCBEBE7),style=MaterialTheme.typography.bodyMedium)
 val total=current?.optInt("total")?:projects.sumOf{it.optInt("total")};val done=current?.optInt("completed")?:projects.sumOf{it.optInt("completed")};val progress by animateFloatAsState(if(total>0)done.toFloat()/total else 0f,tween(350),label="Project progress")
 LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)),color=Color(0xFF8EF3E4),trackColor=Color.White.copy(alpha=.18f))
 Text("$done of $total tasks complete",color=Color.White,style=MaterialTheme.typography.labelMedium)
 }
 if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
 if(isOwner)Button(onClick={title="";desc="";due="";assignee=null;editor=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text(if(current==null)"Create project" else "Add task")}
 if(current==null){LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){items(projects,key={it.getString("id")}){p->ElevatedCard(onClick={selected=p.getString("id")},shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(p.optString("title"),style=MaterialTheme.typography.titleLarge);Text(p.optString("description"));Text("${p.optInt("completed")} / ${p.optInt("total")} tasks completed",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)}}};if(projects.isEmpty()&&!loading)item{Text("No projects yet. Start with a shared goal.")}}}
 else{LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){items(tasks,key={it.getString("id")}){t->ElevatedCard(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(t.optString("title"),style=MaterialTheme.typography.titleMedium);val who=t.optString("assignee_name").takeUnless{it=="null"}.orEmpty();val date=t.optString("due_date").takeUnless{it=="null"}.orEmpty();Text(if(who.isBlank())"Unassigned" else who,style=MaterialTheme.typography.bodySmall);if(date.isNotBlank())Text("Due $date",style=MaterialTheme.typography.bodySmall);Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("todo" to "To do","doing" to "In progress","done" to "Done").forEach{(v,label)->FilterChip(selected=t.optString("status")==v,onClick={act("sera_update_task_status",JSONObject().put("p_task",t.getString("id")).put("p_status",v))},enabled=!busy&&(isOwner||t.optString("assignee_id")==userId),label={Text(label)})}}}}};if(tasks.isEmpty()&&!loading)item{Text("No tasks yet.")}}}
 }
 if(editor)SeraSignatureSheet(onDismissRequest={if(!busy)editor=false}){Column(Modifier.fillMaxWidth().imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(if(current==null)"New project" else "New task",style=MaterialTheme.typography.headlineSmall);OutlinedTextField(title,{title=it.take(if(current==null)100 else 150)},label={Text("Title")},modifier=Modifier.fillMaxWidth());if(current==null)OutlinedTextField(desc,{desc=it.take(1000)},label={Text("Description")},modifier=Modifier.fillMaxWidth())else{OutlinedTextField(due,{due=it.take(10)},label={Text("Deadline (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth(),singleLine=true);Box{OutlinedButton(onClick={menu=true}){Text(members.firstOrNull{it.optString("member_id")==assignee}?.optString("full_name")?:"Assign member")};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){DropdownMenuItem(text={Text("Unassigned")},onClick={assignee=null;menu=false});members.forEach{m->DropdownMenuItem(text={Text(m.optString("full_name"))},onClick={assignee=m.optString("member_id");menu=false})}}}}
 if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);Button(onClick={if(current==null)act("sera_create_space_project",JSONObject().put("p_space",spaceId).put("p_title",title).put("p_description",desc))else act("sera_create_project_task",JSONObject().put("p_project",current.getString("id")).put("p_title",title).put("p_assignee",assignee?:JSONObject.NULL).put("p_due",if(due.isBlank())JSONObject.NULL else due))},enabled=!busy&&title.trim().length>=(if(current==null)3 else 1),modifier=Modifier.fillMaxWidth()){Text(if(busy)"Saving…" else "Save")}}}
}
