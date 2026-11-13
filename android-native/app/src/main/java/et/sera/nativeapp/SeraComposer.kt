package et.sera.nativeapp

import android.content.Context
import kotlinx.coroutines.launch
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil3.compose.AsyncImage
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp

private class ComposerDrafts(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(context, "sera_composer_drafts", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun load(userId: String) = preferences.getString(userId, "") ?: ""
    fun save(userId: String, text: String) { preferences.edit().putString(userId, text).apply() }
    fun audience(userId:String)=preferences.getString("$userId:audience","public")?:"public"
    fun saveAudience(userId:String,value:String){preferences.edit().putString("$userId:audience",value).apply()}
    fun details(userId:String)=runCatching{org.json.JSONObject(preferences.getString("$userId:details","{}")?:"{}")}.getOrDefault(org.json.JSONObject())
    fun saveDetails(userId:String,value:org.json.JSONObject){preferences.edit().putString("$userId:details",value.toString()).apply()}
    fun saveAll(userId:String,body:String,audience:String,fields:org.json.JSONObject){preferences.edit().putString(userId,body).putString("$userId:audience",audience).putString("$userId:details",fields.toString()).apply()}
    fun clear(userId: String) { preferences.edit().remove(userId).remove("$userId:audience").remove("$userId:details").apply() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeraComposer(userId: String, token: String, onPublished: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val drafts = remember(context) { runCatching { ComposerDrafts(context) }.getOrNull() }
    var editor by rememberSaveable(userId,stateSaver=TextFieldValue.Saver){mutableStateOf(TextFieldValue(runCatching{drafts?.load(userId)}.getOrNull().orEmpty()))}
    val body=editor.text
    val restored=remember(userId){runCatching{drafts?.details(userId)}.getOrNull()?:org.json.JSONObject()}
    var title by rememberSaveable(userId){mutableStateOf(restored.optString("title"))}
    var quoteSource by rememberSaveable(userId){mutableStateOf(restored.optString("quote_source"))}
    var category by rememberSaveable(userId){mutableStateOf(restored.optString("category"))}
    var location by rememberSaveable(userId){mutableStateOf(restored.optString("location"))}
    var skills by rememberSaveable(userId){mutableStateOf(restored.optString("skills"))}
    var projectUrl by rememberSaveable(userId){mutableStateOf(restored.optString("project_url"))}
    var detailsOpen by rememberSaveable(userId){mutableStateOf(false)}
    var emojiOpen by remember{mutableStateOf(false)}
    var audience by rememberSaveable(userId){mutableStateOf(runCatching{drafts?.audience(userId)}.getOrNull()?:"public")}
    var audienceOpen by remember{mutableStateOf(false)}
    val audiences=linkedMapOf("public" to "Public","connections" to "Connections","followers" to "Followers","only_me" to "Only me")
    var kind by rememberSaveable(userId){mutableStateOf(restored.optString("kind","idea").takeIf{it in listOf("idea","quote","talent","project","achievement","question","photos")}?: "idea")}
    val kinds=linkedMapOf("idea" to "Idea","photos" to "Photo","quote" to "Quote","talent" to "Talent","project" to "Project","achievement" to "Achievement","question" to "Discussion")
    var saved by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var published by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var publishMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val selectedImages = remember { mutableStateListOf<Uri>() }
    var expandedImage by remember { mutableStateOf<Uri?>(null) }
    var mediaError by remember { mutableStateOf("") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        if (uris.isNotEmpty()) {
            val valid = uris.size <= 4 && uris.distinct().size == uris.size &&
                uris.all { context.contentResolver.getType(it) in listOf("image/jpeg","image/png","image/webp") && mediaSizeAllowed(context, it, 3L * 1024 * 1024) } && uris.sumOf{mediaBytes(context,it)} <= 3L*1024*1024
            if (valid) {
                selectedImages.clear()
                selectedImages.addAll(uris)
                kind="photos"
                mediaError = ""
            } else mediaError = "Choose up to four JPG, PNG or WebP photos totaling 3 MB or less."
        }
    }
    var identity by remember(userId){mutableStateOf("")}
    var avatar by remember(userId){mutableStateOf<String?>(null)}
    var headline by remember(userId){mutableStateOf("")}
    var verified by remember(userId){mutableStateOf(false)}
    LaunchedEffect(userId,token){kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){loadSeraIdentity(token).first}?.let{identity=it.lineSequence().firstOrNull().orEmpty()};avatar=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){loadSeraCurrentAvatar(token,userId)};headline=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){spaceRows(spaceCall(token,"sera_feed_headlines",org.json.JSONObject().put("p_ids",org.json.JSONArray(listOf(userId)))).first).firstOrNull()?.optString("headline").orEmpty()};verified=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){userId in identityBadges(token,listOf(userId))}}
    val hasContent = body.isNotBlank()||title.isNotBlank()||quoteSource.isNotBlank()||category.isNotBlank()||location.isNotBlank()||skills.isNotBlank()||projectUrl.isNotBlank()
    val fields=org.json.JSONObject().put("kind",kind).put("title",title).put("quote_source",quoteSource).put("category",category).put("location",location).put("skills",skills).put("project_url",projectUrl)
    fun persistDraft():Boolean=runCatching{drafts?.saveAll(userId,body,audience,fields);drafts!=null}.getOrDefault(false)
    val accent = Color(0xFF087E83)
    val ink = MaterialTheme.colorScheme.onSurface
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val paper = if(light) Color(0xFFFFFFFF) else MaterialTheme.colorScheme.surface
    val soft = if(light) Color(0xFFF3F8FA) else MaterialTheme.colorScheme.surfaceVariant
    val line = if(light) Color(0xFFDCE8ED) else MaterialTheme.colorScheme.outlineVariant
    val muted = if(light) Color(0xFF64748B) else MaterialTheme.colorScheme.onSurfaceVariant
    val actionInk = if(light) Color(0xFF087E83) else Color(0xFF72DADB)
    val canPublish = !publishing && (kind!="photos"||selectedImages.isNotEmpty()) &&
        (selectedImages.isNotEmpty()||body.trim().isNotEmpty()) && (kind!="quote"||body.length in 3..600)
    fun close() {
        focus.clearFocus()
        onDismiss()
    }
    fun requestClose() {
        if (publishing) return
        if (hasContent || selectedImages.isNotEmpty()) confirmClose = true
        else close()
    }
    BackHandler { requestClose() }
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text("Keep your idea?") },
        text = { Text("Your writing is saved privately as a draft. Selected media is not saved.") },
        confirmButton = {
            TextButton(onClick = {
                val stored = persistDraft()
                confirmClose = false
                if (stored) close()
                else publishMessage = "Secure draft storage is unavailable. Copy your text before closing."
            }) { Text("Save and close") }
        },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep editing") } },
        shape = RoundedCornerShape(22.dp)
    )
    LaunchedEffect(userId,body,audience,kind,title,quoteSource,category,location,skills,projectUrl){if(!published)persistDraft()}
    expandedImage?.let { uri ->
        SeraMediaLightbox(url = uri.toString(), description = "Selected image preview", onDismiss = { expandedImage = null })
    }
    SeraSignatureSheet(onDismissRequest = { requestClose() },canDismiss=!publishing&&!hasContent&&selectedImages.isEmpty(), surfaceColor=paper) {
        val keyboardOpen=WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)>0
        Column(
            Modifier.fillMaxWidth().heightIn(max = 720.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(if(keyboardOpen)4.dp else 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Create a post", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = ink, fontSize=if(keyboardOpen)19.sp else 24.sp)
                }
                if(keyboardOpen)Text("${body.length} / ${if(kind=="quote")600 else 10000}",style=MaterialTheme.typography.labelSmall,color=muted,maxLines=1)
                IconButton(onClick = { requestClose() },modifier=Modifier.background(soft,CircleShape)) { Icon(Icons.Outlined.Close, "Close composer", tint = actionInk) }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(keyboardOpen)8.dp else 12.dp)){
                SeraAccountAvatar(identity,avatar,if(keyboardOpen)32 else 48)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(if(keyboardOpen)0.dp else 4.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(identity.ifBlank{"Your profile"},fontWeight=FontWeight.Bold,color=ink,fontSize=if(keyboardOpen)14.sp else 17.sp,modifier=Modifier.weight(1f,fill=false),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if(verified)SeraIdentityBadge()
                    }
                    if(!keyboardOpen){
                        if(headline.isNotBlank())Text(headline,color=muted,style=MaterialTheme.typography.bodySmall)
                        SeraComposerAudience(audiences,audience,audienceOpen,{audienceOpen=it},{audience=it},!publishing,soft,line,actionInk,false)
                    }
                }
                if(keyboardOpen)SeraComposerAudience(audiences,audience,audienceOpen,{audienceOpen=it},{audience=it},!publishing,soft,line,actionInk,true)
            }
            Column(
                Modifier.weight(1f, fill = keyboardOpen).verticalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(if(keyboardOpen)6.dp else 16.dp)
            ) {
            BasicTextField(
                value = editor, onValueChange = {
                    if (it.text.length <= if(kind=="quote")600 else 10000) { editor = it; saved = false }
                },
                enabled=!publishing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp)
                    .background(soft,RoundedCornerShape(20.dp)).border(1.dp,line,RoundedCornerShape(20.dp)),
                textStyle=TextStyle(color=ink,fontSize=19.sp,lineHeight=27.sp),
                cursorBrush=SolidColor(accent),
                decorationBox={inner->Box(Modifier.fillMaxWidth().padding(16.dp)){if(body.isEmpty())Text(stringResource(R.string.composer_placeholder),color=muted,fontSize=18.sp);inner()}},
                singleLine=false,
                keyboardOptions = seraMultilineKeyboard,
                minLines = 8,
                maxLines=20
            )
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                SeraComposerTool("Photos",Icons.Outlined.Image,actionInk,soft,line,!publishing,Modifier.weight(1f),compact=keyboardOpen){
                    imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SeraComposerTool("Emoji",Icons.Outlined.EmojiEmotions,actionInk,soft,line,!publishing,Modifier.weight(1f),compact=keyboardOpen){emojiOpen=!emojiOpen}
            }
            if(emojiOpen)LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){items(listOf("✨","👍","👏","💡","🎉","❤️","🚀","🤝","😊","🔥")){emoji->TextButton(onClick={val start=editor.selection.min;val end=editor.selection.max;val next=body.substring(0,start)+emoji+body.substring(end);if(next.length<=if(kind=="quote")600 else 10000)editor=TextFieldValue(next,TextRange(start+emoji.length));emojiOpen=false}){Text(emoji,fontSize=22.sp)}}}
            if (mediaError.isNotBlank()) Text(mediaError, color = MaterialTheme.colorScheme.error)
            if (selectedImages.isNotEmpty()) {
                Text("${selectedImages.size} of 4 images selected", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(selectedImages.toList(), key = { it.toString() }) { uri ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AsyncImage(model = uri, contentDescription = "Selected photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(132.dp).clip(RoundedCornerShape(15.dp))
                                    .clickable { expandedImage = uri })
                            TextButton(enabled=!publishing,onClick = {
                                selectedImages.remove(uri)
                                if (selectedImages.isEmpty()&&kind=="photos")kind="idea"
                                if (expandedImage == uri) expandedImage = null
                            }) { Text("Remove photo", fontSize = 12.sp, color = accent) }
                        }
                    }
                }
            }
                Column(verticalArrangement=Arrangement.spacedBy(if(keyboardOpen)2.dp else 8.dp)){
                    if(!keyboardOpen)Text("Post type",fontWeight=FontWeight.SemiBold,color=ink,fontSize=14.sp)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        items(kinds.entries.toList(),key={it.key}){(key,label)->
                            FilterChip(selected=kind==key,enabled=!publishing,onClick={
                                kind=key
                                if(key=="photos"&&selectedImages.isEmpty())imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },label={Text(label,fontSize=13.sp,fontWeight=FontWeight.Medium)},shape=RoundedCornerShape(50),
                                colors=FilterChipDefaults.filterChipColors(containerColor=paper,labelColor=ink,selectedContainerColor=if(light)Color(0xFFE0F6F5) else soft,selectedLabelColor=actionInk))
                        }
                    }
                }
                if(kind=="quote")OutlinedTextField(value=quoteSource,onValueChange={quoteSource=it.take(120)},label={Text("Quote attribution (optional)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedButton(onClick={detailsOpen=!detailsOpen},enabled=!publishing,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,line),contentPadding=PaddingValues(horizontal=if(keyboardOpen)8.dp else 14.dp,vertical=if(keyboardOpen)2.dp else 12.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=soft,contentColor=ink)){
                    Icon(Icons.Outlined.EditNote,null,Modifier.size(21.dp),tint=actionInk)
                    Spacer(Modifier.width(10.dp))
                    Text(if(detailsOpen)"Hide post details" else "Add headline, skills or project details",modifier=Modifier.weight(1f),fontSize=13.sp)
                    Icon(if(detailsOpen)Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,null,Modifier.size(19.dp),tint=muted)
                }
                if(detailsOpen){OutlinedTextField(value=title,onValueChange={title=it.take(140)},label={Text("Headline")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(value=category,onValueChange={category=it.take(60)},label={Text("Category")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(value=location,onValueChange={location=it.take(120)},label={Text("Location")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(value=skills,onValueChange={skills=it.take(400)},label={Text("Skills (comma separated)")},modifier=Modifier.fillMaxWidth());OutlinedTextField(value=projectUrl,onValueChange={projectUrl=it.take(500)},label={Text("Project link (https://)")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
                if(!keyboardOpen)Text("${body.length} / ${if(kind=="quote")600 else 10000}",style=MaterialTheme.typography.labelSmall,color=muted)
                if (selectedImages.isNotEmpty()) Text("Photos are uploaded securely.", style = MaterialTheme.typography.bodySmall, color = accent)
            }
            HorizontalDivider(color = line)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    saved = persistDraft()
                    if (!saved) publishMessage = "Secure draft storage is unavailable."
                    focus.clearFocus()
                }, enabled = hasContent && !publishing,
                    modifier = Modifier.weight(1f).heightIn(min = if(keyboardOpen)48.dp else 50.dp),
                    shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, line),colors=ButtonDefaults.outlinedButtonColors(contentColor=actionInk)) { Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.save_draft)) }
            Button(onClick = {
                if (publishing) return@Button
                publishing = true; publishMessage = ""
                val submissionImages=selectedImages.toList(); val submissionBody=body; val submissionAudience=audience; val submissionKind=kind; val submissionFields=fields
                scope.launch {
                    val result = if (submissionImages.isNotEmpty())
                        publishNativeImages(context, token, submissionBody, submissionImages,submissionAudience,submissionFields)
                    else publishNativeText(token, submissionBody,submissionAudience,submissionKind,submissionFields)
                    publishing = false
                    if (result.first) { published=true; runCatching { drafts?.clear(userId) }; onPublished() }
                    else publishMessage = result.second
                }
            }, enabled = canPublish,
                modifier = Modifier.weight(1.35f).heightIn(min = if(keyboardOpen)48.dp else 50.dp).then(if(canPublish)Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF08AAA9),Color(0xFF00666D))),RoundedCornerShape(24.dp)) else Modifier),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,contentColor=Color.White,disabledContainerColor=soft,disabledContentColor=muted)) {
                Icon(Icons.Outlined.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (publishing) "Publishing…" else "Post", fontWeight = FontWeight.Bold)
            }
            }
            if (publishing) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(if(selectedImages.isNotEmpty()) "Uploading photos…" else "Publishing post…",style=MaterialTheme.typography.labelSmall) }
            if (publishMessage.isNotBlank()) Text(publishMessage, color = MaterialTheme.colorScheme.error)
            if (saved) Text(stringResource(R.string.draft_saved), color = MaterialTheme.colorScheme.primary)

        }
    }
}

@Composable
private fun SeraComposerAudience(audiences:Map<String,String>,audience:String,open:Boolean,onOpen:(Boolean)->Unit,onSelect:(String)->Unit,enabled:Boolean,soft:Color,line:Color,tint:Color,compact:Boolean){
    Box{
        OutlinedButton(enabled=enabled,onClick={onOpen(true)},shape=RoundedCornerShape(50),border=BorderStroke(1.dp,line),
            colors=ButtonDefaults.outlinedButtonColors(containerColor=soft,contentColor=tint),
            contentPadding=PaddingValues(horizontal=if(compact)8.dp else 12.dp,vertical=if(compact)0.dp else 4.dp)){
            Icon(Icons.Outlined.Public,null,Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text((if(compact)"" else "Audience: ")+(audiences[audience]?:"Only me"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
            Icon(Icons.Outlined.ExpandMore,null,Modifier.size(17.dp))
        }
        DropdownMenu(expanded=open,onDismissRequest={onOpen(false)}){
            audiences.forEach{(key,label)->DropdownMenuItem(text={Text(label)},onClick={onSelect(key);onOpen(false)})}
        }
    }
}

@Composable
private fun SeraComposerTool(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,tint:Color,soft:Color,line:Color,enabled:Boolean,modifier:Modifier=Modifier,compact:Boolean=false,onClick:()->Unit){
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=if(compact)48.dp else 64.dp),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,line),
        colors=ButtonDefaults.outlinedButtonColors(containerColor=soft,contentColor=tint),
        contentPadding=PaddingValues(if(compact)4.dp else 8.dp)){
        if(compact)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            Icon(icon,null,Modifier.size(18.dp))
            Text(label,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        }else Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
            Icon(icon,null,Modifier.size(23.dp))
            Text(label,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        }
    }
}

private fun mediaSizeAllowed(context: Context, uri: Uri, maxBytes: Long): Boolean {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return false
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index < 0 || cursor.isNull(index)) return false
            val size = cursor.getLong(index)
            size in 1..maxBytes
        } ?: false
    } catch (_: Exception) { false }
}

private fun mediaBytes(context:Context,uri:Uri):Long=runCatching{context.contentResolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use{if(it.moveToFirst())it.getLong(0) else Long.MAX_VALUE}?:Long.MAX_VALUE}.getOrDefault(Long.MAX_VALUE)
