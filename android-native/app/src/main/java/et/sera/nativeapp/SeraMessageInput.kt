package et.sera.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val SERA_MESSAGE_LIMIT = 4000

/** Keep selection and IME composing spans local; persist only text changes. */
@Composable
internal fun SeraMessageInput(
    draft: String, onDraftChange: (String) -> Unit,
    ink: Color, soft: Color, muted: Color, accent: Color,
    readOnly: Boolean, modifier: Modifier = Modifier, replyFocusId:String?=null
) {
    var editor by remember { mutableStateOf(TextFieldValue(draft, TextRange(draft.length))) }
    val value = if (editor.text == draft) editor else TextFieldValue(draft, TextRange(draft.length))
    SideEffect { if (editor.text != draft) editor = value }
    var focused by remember { mutableStateOf(false) }
    val requester=remember{FocusRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(replyFocusId){if(replyFocusId!=null&&!readOnly){requester.requestFocus();keyboard?.show()}}
    var emojiOpen by remember { mutableStateOf(false) }
    var limitReached by remember { mutableStateOf(false) }
    LaunchedEffect(readOnly) { if (readOnly) emojiOpen = false }
    LaunchedEffect(draft) { if (draft.length < SERA_MESSAGE_LIMIT) limitReached = false }
    fun update(next: TextFieldValue) {
        if (readOnly) return
        if (next.text.length > SERA_MESSAGE_LIMIT) { limitReached = true; return }
        editor = next
        if (next.text != draft) onDraftChange(next.text)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val shape = RoundedCornerShape(24.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(soft)
                .border(1.dp, if (focused) accent.copy(alpha = .55f) else Color.Transparent, shape)
                .padding(start = 13.dp, end = 3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            BasicTextField(
                value = value, onValueChange = ::update, readOnly = readOnly,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                    .focusRequester(requester).onFocusChanged { focused = it.isFocused }
                    .testTag("sera_message_input")
                    .semantics { contentDescription = "Message input" },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = ink, fontSize = 15.sp, lineHeight = 21.sp),
                cursorBrush = SolidColor(accent), singleLine = false, minLines = 1, maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true, keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (value.text.isEmpty()) Text("Message…", color = muted, fontSize = 15.sp, lineHeight = 21.sp)
                        inner()
                    }
                }
            )
            Box {
                IconButton(onClick = { emojiOpen = true }, enabled = !readOnly, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.SentimentSatisfiedAlt, "Insert emoji", tint = muted, modifier = Modifier.size(21.dp))
                }
                DropdownMenu(expanded = emojiOpen, onDismissRequest = { emojiOpen = false }) {
                    listOf("😊", "👍", "❤️", "👏", "🔥", "✨").forEach { emoji ->
                        DropdownMenuItem(text = { Text(emoji, fontSize = 23.sp) }, onClick = {
                            val current = value
                            val start = current.selection.min
                            val end = current.selection.max
                            val next = current.text.replaceRange(start, end, emoji)
                            update(TextFieldValue(next, TextRange(start + emoji.length)))
                            emojiOpen = false
                        })
                    }
                }
            }
        }
        if (draft.length >= 3800 || limitReached) {
            Text(if (limitReached) "Message limit: 4,000 characters" else "${draft.length} / 4000",
                color = if (limitReached) MaterialTheme.colorScheme.error else muted,
                fontSize = 11.sp, modifier = Modifier.align(Alignment.End).padding(end = 8.dp))
        }
    }
}

@Composable
internal fun SeraMessageSendButton(enabled: Boolean, busy: Boolean, teal: Color, onSend: () -> Unit) {
    FilledIconButton(
        onClick = onSend, enabled = enabled && !busy,
        modifier = Modifier.size(48.dp).testTag("sera_message_send"),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = teal, contentColor = Color.White,
            disabledContainerColor = teal.copy(alpha = .25f), disabledContentColor = Color.White.copy(alpha = .75f)
        )
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = "Sending message" },
            color = Color.White, strokeWidth = 2.dp)
        else Icon(Icons.Outlined.Send, "Send message", modifier = Modifier.size(22.dp))
    }
}
