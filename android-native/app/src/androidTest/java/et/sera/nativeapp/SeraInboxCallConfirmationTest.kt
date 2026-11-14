package et.sera.nativeapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeraInboxCallConfirmationTest {
 @get:Rule val compose=createComposeRule()
 private val contact=NativeConversation("conversation","peer","Hana",preview="Hello")
 @Test fun dismissDoesNotStartCallOrOpenConversation(){
  val host=SeraGlobalCallState()
  var opened=0
  compose.setContent{
   val p=seraChatPalette(false)
   MaterialTheme(colorScheme=seraColorScheme(false)){
    CompositionLocalProvider(LocalSeraGlobalCall provides host){
     SeraNativeConversationList(listOf(contact),"",false,"",p.ink,p.quote,p.canvas,p.muted,p.accent,p.accent,null,{}, {}, {},{opened++})
    }
   }
  }
  compose.onNodeWithTag("sera_inbox_call_conversation").performClick()
  compose.onNodeWithTag("sera_inbox_call_no").assertExists()
  compose.runOnIdle{assertNull(host.target);assertFalse(host.pinned);assertEquals(0,opened)}
  compose.onNodeWithTag("sera_inbox_call_no").performClick()
  compose.onNodeWithTag("sera_inbox_call_no").assertDoesNotExist()
  compose.runOnIdle{assertNull(host.target);assertFalse(host.pinned);assertEquals(0,opened)}
 }
 @Test fun confirmationQueuesExactlyOneCallForCorrectRecipient(){
  val host=SeraGlobalCallState()
  val target=SeraCallTarget("conversation","Hana",null)
  assertTrue(host.startInbox(target))
  assertEquals(target,host.target)
  assertTrue(host.pinned)
  assertFalse(host.startInbox(SeraCallTarget("other","Other",null)))
  assertFalse(host.consumeInboxStart("other"))
  assertTrue(host.consumeInboxStart("conversation"))
  assertFalse(host.consumeInboxStart("conversation"))
 }
}
