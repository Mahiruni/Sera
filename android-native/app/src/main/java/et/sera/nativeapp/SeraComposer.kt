package et.sera.nativeapp

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp

private class ComposerDrafts(context: Context) {
    private val preferences = context.getSharedPreferences("sera_composer_drafts", Context.MODE_PRIVATE)
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
    var confirmDiscard by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val hasContent = body.isNotBlank()
    fun close() {
        focus.clearFocus()
        if (hasContent) onDismiss() else onDismiss()
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
            Text("Create a professional update", style = MaterialTheme.typography.titleLarge)
            Text("Share ideas, work and achievements with your network.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = body, onValueChange = {
                    if (it.length <= 4000) { body = it; saved = false }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                placeholder = { Text("What would you like to share?") },
                supportingText = { Text("${body.length} / 4000 characters") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                minLines = 5
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    drafts.save(userId, body)
                    saved = true
                    focus.clearFocus()
                }, enabled = hasContent, modifier = Modifier.weight(1f)) { Text("Save draft") }
                Button(onClick = {
                    drafts.save(userId, body)
                    saved = true
                    focus.clearFocus()
                }, enabled = hasContent, modifier = Modifier.weight(1f)) { Text("Keep for later") }
            }
            if (saved) Text("Draft saved on this device.", color = MaterialTheme.colorScheme.primary)
            Text("Publishing is disabled until SERA's secure server-side moderation flow is connected. Drafts remain on this device.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { close() }) { Text("Close") }
        }
    }
}
