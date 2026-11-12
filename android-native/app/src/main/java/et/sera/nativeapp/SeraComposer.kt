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
    fun clear(userId: String) { preferences.edit().remove(userId).apply() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeraComposer(userId: String, token: String, onPublished: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val drafts = remember(context) { runCatching { ComposerDrafts(context) }.getOrNull() }
    var body by rememberSaveable(userId) {
        mutableStateOf(runCatching { drafts?.load(userId) }.getOrNull().orEmpty())
    }
    var saved by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var publishMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val selectedImages = remember { mutableStateListOf<Uri>() }
    var expandedImage by remember { mutableStateOf<Uri?>(null) }
    var mediaError by remember { mutableStateOf("") }
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        if (uris.isNotEmpty()) {
            val valid = uris.size <= 4 && uris.distinct().size == uris.size &&
                uris.all { mediaSizeAllowed(context, it, 20L * 1024 * 1024) }
            if (valid) {
                selectedImages.clear()
                selectedImages.addAll(uris)
                selectedVideo = null
                mediaError = ""
            } else mediaError = "Choose up to four readable images, each under 20 MB."
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (mediaSizeAllowed(context, uri, 100L * 1024 * 1024)) { selectedVideo = uri; selectedImages.clear(); mediaError = "" }
            else mediaError = "Video exceeds the 100 MB attachment limit or cannot be read."
        }
    }
    var identity by remember(userId){mutableStateOf("")}
    var avatar by remember(userId){mutableStateOf<String?>(null)}
    var headline by remember(userId){mutableStateOf("")}
    LaunchedEffect(userId,token){kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){loadSeraIdentity(token).first}?.let{identity=it.lineSequence().firstOrNull().orEmpty()};avatar=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){loadSeraCurrentAvatar(token,userId)};headline=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){spaceRows(spaceCall(token,"sera_feed_headlines",org.json.JSONObject().put("p_ids",org.json.JSONArray(listOf(userId)))).first).firstOrNull()?.optString("headline").orEmpty()}}
    val hasContent = body.isNotBlank()
    val accent = Color(0xFF087E83)
    val ink = Color(0xFF173A3F)
    fun close() {
        focus.clearFocus()
        onDismiss()
    }
    fun requestClose() {
        if (publishing) return
        if (body.isNotBlank() || selectedImages.isNotEmpty() || selectedVideo != null) confirmClose = true
        else close()
    }
    BackHandler { requestClose() }
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text("Keep your idea?") },
        text = { Text("Your writing is saved privately as a draft. Selected media is not saved.") },
        confirmButton = {
            TextButton(onClick = {
                val stored = runCatching { drafts?.save(userId, body); drafts != null }.getOrDefault(false)
                confirmClose = false
                if (stored) close()
                else publishMessage = "Secure draft storage is unavailable. Copy your text before closing."
            }) { Text("Save and close") }
        },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep editing") } },
        shape = RoundedCornerShape(22.dp)
    )
    LaunchedEffect(userId, body) { runCatching { drafts?.save(userId, body) } }
    expandedImage?.let { uri ->
        SeraMediaLightbox(url = uri.toString(), description = "Selected image preview", onDismiss = { expandedImage = null })
    }
    SeraSignatureSheet(onDismissRequest = { requestClose() }) {
        val keyboardOpen=WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)>0
        Column(
            Modifier.fillMaxWidth().heightIn(max = 680.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if(keyboardOpen)6.dp else 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = Color(0xFFE4F5F2), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, tint = accent,
                        modifier = Modifier.padding(12.dp).size(25.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Create a post", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = ink)
                    Text("Share your work, ideas or insights", style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF687C80))
                }
                IconButton(onClick = { requestClose() }) { Icon(Icons.Outlined.Close, "Close composer", tint = accent) }
            }
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){if(avatar!=null)SeraMediaImage(avatar!!,"Your profile",96,96,modifier=Modifier.size(38.dp).clip(CircleShape));Column{Text(identity.ifBlank{"Your profile"},fontWeight=FontWeight.SemiBold,color=ink);if(headline.isNotBlank())Text(headline,color=accent,style=MaterialTheme.typography.labelSmall)}}
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            OutlinedTextField(
                value = body, onValueChange = {
                    if (it.length <= 4000) { body = it; saved = false }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = if(keyboardOpen)80.dp else 240.dp),
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text(stringResource(R.string.composer_placeholder)) },
                supportingText = { Text("${body.length} / 4000 characters") },
                singleLine=false,
                keyboardOptions = seraMultilineKeyboard,
                minLines = if(keyboardOpen)2 else 8,
                maxLines=if(keyboardOpen)5 else 20,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = Color(0xFFD8E7E5), focusedContainerColor = Color(0xFFF7FBFA), unfocusedContainerColor = Color(0xFFF7FBFA), cursorColor = accent)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Photos")
                }
                FilledTonalButton(onClick = {
                    videoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }, modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Video preview")
                }
            }
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
                                if (expandedImage == uri) expandedImage = null
                            }) { Text("Remove photo", fontSize = 12.sp, color = accent) }
                        }
                    }
                }
            }
            selectedVideo?.let { uri ->
                SeraVideoPlayer(url = uri.toString(), localPreview = true)
                Text("Video preview only. Upload is not yet available.")
                Text(uri.lastPathSegment?.take(80) ?: "Selected video", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { selectedVideo = null }) { Text("Remove video") }
            }
                if(!keyboardOpen)Text("Keep posts professional. Text drafts stay on this device.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF607478))
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
                        publishNativeImages(context, token, body, selectedImages.toList())
                    else publishNativeText(token, body)
                    publishing = false
                    if (result.first) { runCatching { drafts?.clear(userId) }; body = ""; onPublished() }
                    else publishMessage = result.second
                }
            }, enabled = !publishing && body.trim().length >= 3 && selectedVideo == null,
                modifier = Modifier.weight(1.35f).heightIn(min = 50.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Icon(Icons.Outlined.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (publishing) "Publishing…" else "Publish post", fontWeight = FontWeight.Bold)
            }
            }
            if (publishing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (publishMessage.isNotBlank()) Text(publishMessage, color = MaterialTheme.colorScheme.error)
            if (saved) Text(stringResource(R.string.draft_saved), color = MaterialTheme.colorScheme.primary)
            if (selectedVideo != null) Text("Video publishing is not available yet. Remove the video to publish.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

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
