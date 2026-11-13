package et.sera.nativeapp

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PremiumMessageUiTest {
 @get:Rule val compose=createComposeRule()
 @Test fun quoteNavigationAndAccessibleQuickReaction(){
  var quote=""
  var reaction=""
  val original=NativeMessage("original","peer","Earlier project update","","2026-11-13T10:00:00Z","",emptyList())
  val message=NativeMessage("reply","peer","Thanks for the update","","2026-11-13T10:01:00Z","",emptyList(),replyId=original.id)
  compose.setContent{
   MaterialTheme{
    val context=LocalContext.current
    val playback=remember{SeraChatPlayback(context.applicationContext)}
    var menu by remember{mutableStateOf(false)}
    Column{
     SeraNativeMessageRow(message=message,originalMessage=original,userId="me",peerName="Contact",token="",playback=playback,maxBubbleWidth=300.dp,inboxInk=Color.Black,inboxSoft=Color.LightGray,chatSurface=Color.White,muted=Color.DarkGray,accent=Color(0xFF087E83),outgoing=Color(0xFF087478),incoming=Color(0xFFE7F0EF),menuOpen=menu,onMenu={menu=it},onReply={},onReact={},onForward={},onCopy={},onUnsend={},onReaction={reaction=it},onImage={},onError={},onQuote={quote=it})
    }
   }
  }
  compose.onNodeWithTag("sera_quote_reply",useUnmergedTree=true).performClick()
  compose.runOnIdle{assertEquals("original",quote)}
  compose.onNodeWithTag("sera_message_reply",useUnmergedTree=true).performTouchInput{longClick()}
  compose.onNodeWithContentDescription("React ❤️").performClick()
  compose.runOnIdle{assertEquals("❤️",reaction)}
 }
}
