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
    val drafts = remember(context) { ComposerDrafts(context) }
    var body by rememberSaveable(userId) { mutableStateOf(drafts.load(userId)) }
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
    val hasContent = body.isNotBlank()
    val accent = Color(0xFF087E83)
    val ink = Color(0xFF173A3F)
    fun close() {
        focus.clearFocus()
        onDismiss()
    }
    fun requestClose() {
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
                drafts.save(userId, body)
                confirmClose = false
                close()
            }) { Text("Save and close") }
        },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep editing") } },
        shape = RoundedCornerShape(22.dp)
    )
    LaunchedEffect(userId, body) { drafts.save(userId, body) }
    expandedImage?.let { uri ->
        SeraMediaLightbox(url = uri.toString(), description = "Selected image preview", onDismiss = { expandedImage = null })
    }
    SeraSignatureSheet(onDismissRequest = { requestClose() }) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    Text("Create a professional update", style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF687C80))
                }
                TextButton(onClick = { requestClose() }) { Text("Close", color = accent) }
            }
            OutlinedTextField(
                value = body, onValueChange = {
                    if (it.length <= 4000) { body = it; saved = false }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text(stringResource(R.string.composer_placeholder)) },
                supportingText = { Text("${body.length} / 4000 characters") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                minLines = 5
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Photos")
                }
                OutlinedButton(onClick = {
                    videoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }, modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Video")
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    drafts.save(userId, body)
                    saved = true
                    focus.clearFocus()
                }, enabled = hasContent, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) { Text(stringResource(R.string.save_draft)) }
                Button(onClick = {
                    drafts.save(userId, body)
                    saved = true
                    focus.clearFocus()
                }, enabled = hasContent, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ink)) { Text(stringResource(R.string.keep_later)) }
            }
            Button(onClick = {
                publishing = true; publishMessage = ""
                scope.launch {
                    val result = if (selectedImages.isNotEmpty())
                        publishNativeImages(context, token, body, selectedImages.toList())
                    else publishNativeText(token, body)
                    publishing = false
                    if (result.first) { drafts.clear(userId); body = ""; onPublished() }
                    else publishMessage = result.second
                }
            }, enabled = !publishing && body.trim().length >= 3 && selectedVideo == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Text(if (publishing) "Publishing…" else if (selectedImages.isNotEmpty()) "Publish photo post" else "Publish post", fontWeight = FontWeight.Bold)
            }
            if (publishing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (publishMessage.isNotBlank()) Text(publishMessage, color = MaterialTheme.colorScheme.error)
            if (saved) Text(stringResource(R.string.draft_saved), color = MaterialTheme.colorScheme.primary)
            if (selectedVideo != null) Text("Video publishing is not available yet. Remove the video to publish.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (selectedImages.isNotEmpty()) Text("Photos are checked for safety before your post appears.", style = MaterialTheme.typography.bodySmall, color = accent)
            Text(stringResource(R.string.composer_notice),
                style = MaterialTheme.typography.bodySmall)

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
