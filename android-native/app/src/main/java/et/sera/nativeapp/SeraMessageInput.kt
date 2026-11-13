package et.sera.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
    val palette=seraChatColors()
    var editor by remember { mutableStateOf(TextFieldValue(draft, TextRange(draft.length))) }
    val value = if (editor.text == draft) editor else TextFieldValue(draft, TextRange(draft.length))
    SideEffect { if (editor.text != draft) editor = value }
    var focused by remember { mutableStateOf(false) }
    val requester=remember{FocusRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(replyFocusId, readOnly){if(replyFocusId!=null&&!readOnly){requester.requestFocus();keyboard?.show()}}
    var emojiOpen by remember { mutableStateOf(false) }
    var limitReached by remember { mutableStateOf(false) }
    LaunchedEffect(readOnly) { if (readOnly) emojiOpen = false }
    LaunchedEffect(draft) { if (draft.length < SERA_MESSAGE_LIMIT) limitReached = false }
    fun update(next: TextFieldValue) {
        if (readOnly) return
        if (next.text.length > SERA_MESSAGE_LIMIT && next.text.length > editor.text.length) { limitReached = true; return }
        editor = next
        if (next.text != draft) onDraftChange(next.text)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val shape = RoundedCornerShape(24.dp)
        val inputBorder by animateColorAsState(if(focused) accent else palette.border,tween(160),label="Composer focus")
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(palette.surface)
                .border(1.dp, inputBorder, shape)
                .padding(start = 13.dp, end = 3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            BasicTextField(
                value = value, onValueChange = ::update, readOnly = readOnly,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                    .focusRequester(requester).onFocusChanged { focused = it.isFocused }
                    .testTag("sera_message_input")
                    .semantics { contentDescription = "Message input" },
                textStyle = seraChatBody(MaterialTheme.typography.bodyLarge).copy(color = ink),
                cursorBrush = SolidColor(accent), singleLine = false, minLines = 1, maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true, keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (value.text.isEmpty()) Text("Message…", color = muted, style = seraChatBody(MaterialTheme.typography.bodyLarge))
                        inner()
                    }
                }
            )
            IconButton(onClick = { emojiOpen = !emojiOpen }, enabled = !readOnly, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.SentimentSatisfiedAlt, if(emojiOpen) "Close emoji picker" else "Insert emoji",
                    tint = if(emojiOpen) accent else muted, modifier = Modifier.size(21.dp))
            }
        }
        // Keep the picker in the composer instead of a focusable window. The
        // system IME retains its session, selection and composing text.
        if(emojiOpen && !readOnly){
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                listOf("😊", "👍", "❤️", "👏", "🔥", "✨").forEach { emoji ->
                    TextButton(onClick = {
                        val current = value
                        val start = current.selection.min
                        val end = current.selection.max
                        val next = current.text.replaceRange(start, end, emoji)
                        update(TextFieldValue(next, TextRange(start + emoji.length)))
                        emojiOpen = false
                    }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(emoji, fontSize = 23.sp)
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
    val palette=seraChatColors()
    FilledIconButton(
        onClick = onSend, enabled = enabled && !busy,
        modifier = Modifier.size(48.dp).testTag("sera_message_send"),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = teal, contentColor = palette.onButton,
            disabledContainerColor = palette.quote, disabledContentColor = palette.muted
        )
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = "Sending message" },
            color = palette.onButton, strokeWidth = 2.dp)
        else Icon(Icons.Outlined.Send, "Send message", modifier = Modifier.size(22.dp))
    }
}
