package et.sera.nativeapp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.*
class ScheduledMessageUiTest {
 @get:Rule val compose=createComposeRule()
 @Test fun localTimeConvertsToServerInstant(){
  assertEquals(Instant.parse("2026-11-16T06:00:00Z"),scheduledTime(LocalDate.of(2026,11,16),LocalTime.of(9,0),ZoneId.of("Africa/Addis_Ababa")))
  assertNull(scheduledTime(LocalDate.of(2026,3,8),LocalTime.of(2,30),ZoneId.of("America/New_York")))
 }
 @Test fun toolbarDisablesForwardForAttachments(){
  var starred=false
  compose.setContent{MaterialTheme{SeraMultiSelectToolbar(2,false,false,onClose={},onCopy={},onStar={starred=true},onForward={})}}
  compose.onNodeWithContentDescription("Forward selected text messages").assertIsNotEnabled()
  compose.onNodeWithContentDescription("Star selected messages").performClick()
  compose.runOnIdle{assertTrue(starred)}
 }
}
