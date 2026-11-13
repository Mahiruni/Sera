package et.sera.nativeapp

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class MessageComposerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun multilineInputPreservesDraftAndSendIsExplicit() {
        var draft by mutableStateOf("")
        var sent by mutableStateOf(0)
        var busy by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                Column {
                    SeraMessageInput(draft, { draft = it }, Color.Black, Color.LightGray, Color.DarkGray, Color(0xFF087E83), busy)
                    SeraMessageSendButton(draft.isNotBlank(), busy, Color(0xFF087E83)) { sent++; busy = true }
                }
            }
        }
        val field = compose.onNodeWithTag("sera_message_input")
        val send = compose.onNodeWithTag("sera_message_send")
        send.assertIsNotEnabled()
        field.performClick()
        field.performTextInput("Hello")
        field.performTextInput("\nSecond line")
        compose.runOnIdle { assertEquals("Hello\nSecond line", draft); assertEquals(0, sent) }
        send.assertIsEnabled().performClick()
        send.assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, sent); assertEquals("Hello\nSecond line", draft) }
        compose.runOnIdle { draft = ""; busy = false }
        send.assertIsNotEnabled()
    }

    @Test fun emojiRespectsCursorAndExternalDraftReset() {
        var draft by mutableStateOf("Hello")
        compose.setContent {
            MaterialTheme {
                SeraMessageInput(draft, { draft = it }, Color.Black, Color.LightGray, Color.DarkGray, Color(0xFF087E83), false)
            }
        }
        compose.onNodeWithContentDescription("Insert emoji").performClick()
        compose.onNodeWithText("👍").performClick()
        compose.runOnIdle { assertEquals("Hello👍", draft); draft = "New draft" }
        compose.onNodeWithTag("sera_message_input").assertTextContains("New draft")
    }
    @Test fun inlineEmojiKeepsEditorFocusAndReplacesSelectedText() {
        var draft by mutableStateOf("Hello world")
        compose.setContent {
            MaterialTheme {
                SeraMessageInput(draft, { draft = it }, Color.Black, Color.LightGray, Color.DarkGray, Color(0xFF087E83), false)
            }
        }
        val field = compose.onNodeWithTag("sera_message_input")
        field.performClick()
        field.performTextInputSelection(TextRange(6, 11))
        compose.onNodeWithContentDescription("Insert emoji").performClick()
        field.assertIsFocused()
        compose.onNodeWithText("👍").performClick()
        field.assertIsFocused()
        compose.runOnIdle { assertEquals("Hello 👍", draft) }
        field.performTextInput("\nNext line")
        compose.runOnIdle { assertEquals("Hello 👍\nNext line", draft) }
    }

    @Test fun oversizedExistingDraftCanBeShortened() {
        var draft by mutableStateOf("a".repeat(SERA_MESSAGE_LIMIT + 2))
        compose.setContent {
            MaterialTheme {
                SeraMessageInput(draft, { draft = it }, Color.Black, Color.LightGray, Color.DarkGray, Color(0xFF087E83), false)
            }
        }
        val field = compose.onNodeWithTag("sera_message_input")
        field.performClick()
        field.performTextReplacement("a".repeat(SERA_MESSAGE_LIMIT + 1))
        compose.runOnIdle { assertEquals(SERA_MESSAGE_LIMIT + 1, draft.length) }
        field.performTextReplacement("a".repeat(SERA_MESSAGE_LIMIT))
        compose.runOnIdle { assertEquals(SERA_MESSAGE_LIMIT, draft.length) }
    }

}
