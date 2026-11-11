package et.sera.nativeapp

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil3.compose.AsyncImage
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
fun SeraComposer(userId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val drafts = remember(context) { ComposerDrafts(context) }
    var body by rememberSaveable(userId) { mutableStateOf(drafts.load(userId)) }
    var saved by remember { mutableStateOf(false) }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { selectedImage = uri; selectedVideo = null }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { selectedVideo = uri; selectedImage = null }
    }
    val hasContent = body.isNotBlank()
    fun close() {
        focus.clearFocus()
        onDismiss()
    }
    BackHandler { close() }
    DisposableEffect(userId, body) {
        onDispose { drafts.save(userId, body) }
    }
    ModalBottomSheet(onDismissRequest = { close() }, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.composer_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.composer_hint), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = body, onValueChange = {
                    if (it.length <= 4000) { body = it; saved = false }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                placeholder = { Text(stringResource(R.string.composer_placeholder)) },
                supportingText = { Text("${body.length} / 4000 characters") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                minLines = 5
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.weight(1f)) { Text("Add image") }
                OutlinedButton(onClick = {
                    videoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }, modifier = Modifier.weight(1f)) { Text("Add video") }
            }
            selectedImage?.let { uri ->
                AsyncImage(model = uri, contentDescription = "Selected image preview",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp))
                TextButton(onClick = { selectedImage = null }) { Text("Remove image") }
            }
            selectedVideo?.let { uri ->
                Text("Video selected for preview. Upload is not yet available.")
                Text(uri.lastPathSegment?.take(80) ?: "Selected video", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { selectedVideo = null }) { Text("Remove video") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    drafts.save(userId, body)
                    saved = true
                    focus.clearFocus()
                }, enabled = hasContent, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save_draft)) }
                Button(onClick = {
                    drafts.save(userId, body)
                    saved = true
                    focus.clearFocus()
                }, enabled = hasContent, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.keep_later)) }
            }
            if (saved) Text(stringResource(R.string.draft_saved), color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.composer_notice),
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { close() }) { Text(stringResource(R.string.close)) }
        }
    }
}
