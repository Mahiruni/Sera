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
    var kindOpen by remember{mutableStateOf(false)}
    val kinds=linkedMapOf("idea" to "Idea","photos" to "Photo","quote" to "Quote","talent" to "Talent","project" to "Project","achievement" to "Achievement","question" to "Discussion")
    var saved by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
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
    fun persistDraft():Boolean=runCatching{drafts?.save(userId,body);drafts?.saveAudience(userId,audience);drafts?.saveDetails(userId,fields);drafts!=null}.getOrDefault(false)
    val accent = Color(0xFF087E83)
    val ink = MaterialTheme.colorScheme.onSurface
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
    LaunchedEffect(userId,body,audience,kind,title,quoteSource,category,location,skills,projectUrl){persistDraft()}
    expandedImage?.let { uri ->
        SeraMediaLightbox(url = uri.toString(), description = "Selected image preview", onDismiss = { expandedImage = null })
    }
    SeraSignatureSheet(onDismissRequest = { requestClose() },canDismiss=!publishing&&!hasContent&&selectedImages.isEmpty()) {
        val keyboardOpen=WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)>0
        Column(
            Modifier.fillMaxWidth().heightIn(max = 720.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(if(keyboardOpen)6.dp else 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Create a post", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = ink)
                }
                IconButton(onClick = { requestClose() }) { Icon(Icons.Outlined.Close, "Close composer", tint = accent) }
            }
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){SeraAccountAvatar(identity,avatar,42);Column(Modifier.weight(1f)){Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){Text(identity.ifBlank{"Your profile"},fontWeight=FontWeight.SemiBold,color=ink,modifier=Modifier.weight(1f,fill=false));if(verified)SeraIdentityBadge()};if(headline.isNotBlank())Text(headline,color=accent,style=MaterialTheme.typography.labelSmall);Box{TextButton(onClick={audienceOpen=true},contentPadding=PaddingValues(0.dp)){Text("Audience: "+(audiences[audience]?:"Only me"))};DropdownMenu(expanded=audienceOpen,onDismissRequest={audienceOpen=false}){audiences.forEach{(key,label)->DropdownMenuItem(text={Text(label)},onClick={audience=key;audienceOpen=false})}}}}}
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            BasicTextField(
                value = editor, onValueChange = {
                    if (it.text.length <= if(kind=="quote")600 else 10000) { editor = it; saved = false }
                },
                enabled=!publishing,
                modifier = Modifier.fillMaxWidth().heightIn(min = if(keyboardOpen)80.dp else 240.dp),
                textStyle=TextStyle(color=ink,fontSize=19.sp,lineHeight=27.sp),
                cursorBrush=SolidColor(accent),
                decorationBox={inner->Box(Modifier.fillMaxWidth().padding(vertical=12.dp)){if(body.isEmpty())Text(stringResource(R.string.composer_placeholder),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=19.sp);inner()}},
                singleLine=false,
                keyboardOptions = seraMultilineKeyboard,
                minLines = if(keyboardOpen)2 else 8,
                maxLines=if(keyboardOpen)5 else 20
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp)) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Photos")
                }
                TextButton(onClick={emojiOpen=!emojiOpen},enabled=!publishing,modifier=Modifier.weight(1f)){Text("Emoji")}
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
                            TextButton(onClick = {
                                selectedImages.remove(uri)
                                if (selectedImages.isEmpty()&&kind=="photos")kind="idea"
                                if (expandedImage == uri) expandedImage = null
                            }) { Text("Remove photo", fontSize = 12.sp, color = accent) }
                        }
                    }
                }
            }
                Box{TextButton(onClick={kindOpen=true}){Text("Post type: "+(kinds[kind]?:"Idea"))};DropdownMenu(expanded=kindOpen,onDismissRequest={kindOpen=false}){kinds.forEach{(key,label)->DropdownMenuItem(text={Text(label)},onClick={kind=key;kindOpen=false;if(key=="photos"&&selectedImages.isEmpty())imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))})}}}
                if(kind=="quote")OutlinedTextField(value=quoteSource,onValueChange={quoteSource=it.take(120)},label={Text("Quote attribution (optional)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                TextButton(onClick={detailsOpen=!detailsOpen}){Text(if(detailsOpen)"Hide post details" else "Add headline, skills or project details")}
                if(detailsOpen){OutlinedTextField(value=title,onValueChange={title=it.take(140)},label={Text("Headline")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(value=category,onValueChange={category=it.take(80)},label={Text("Category")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(value=location,onValueChange={location=it.take(120)},label={Text("Location")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(value=skills,onValueChange={skills=it.take(500)},label={Text("Skills (comma separated)")},modifier=Modifier.fillMaxWidth());OutlinedTextField(value=projectUrl,onValueChange={projectUrl=it.take(500)},label={Text("Project link (https://)")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
                Text("${body.length} / ${if(kind=="quote")600 else 10000}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if (selectedImages.isNotEmpty()) Text("Photos are uploaded securely.", style = MaterialTheme.typography.bodySmall, color = accent)
            }
            HorizontalDivider(color = Color(0xFFE4EFED))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    saved = runCatching { drafts?.save(userId, body); drafts != null }.getOrDefault(false)
                    if (!saved) publishMessage = "Secure draft storage is unavailable."
                    focus.clearFocus()
                }, enabled = hasContent && !publishing,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Color(0xFFD8E7E5))) { Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.save_draft)) }
            Button(onClick = {
                if (publishing) return@Button
                publishing = true; publishMessage = ""
                scope.launch {
                    val result = if (selectedImages.isNotEmpty())
                        publishNativeImages(context, token, body, selectedImages.toList(),audience,fields)
                    else publishNativeText(token, body,audience,kind,fields)
                    publishing = false
                    if (result.first) { runCatching { drafts?.clear(userId) }; editor = TextFieldValue(""); onPublished() }
                    else publishMessage = result.second
                }
            }, enabled = !publishing && (kind!="photos"||selectedImages.isNotEmpty()) && (selectedImages.isNotEmpty()||body.trim().isNotEmpty()) && (kind!="quote"||body.length in 3..600),
                modifier = Modifier.weight(1.35f).heightIn(min = 50.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Icon(Icons.Outlined.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (publishing) "Publishing…" else "Post", fontWeight = FontWeight.Bold)
            }
            }
            if (publishing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (publishMessage.isNotBlank()) Text(publishMessage, color = MaterialTheme.colorScheme.error)
            if (saved) Text(stringResource(R.string.draft_saved), color = MaterialTheme.colorScheme.primary)

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
