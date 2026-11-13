package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeraThreadHeaderTest {
 @get:Rule val compose=createComposeRule()
 @Test fun fullNameAndRelativeStatusFitNarrowIdentity(){
  compose.setContent {
   val p=seraChatPalette(false)
   MaterialTheme(colorScheme=seraColorScheme(false)){
    Box(Modifier.width(112.dp)){
     SeraThreadIdentity("Halima Aman","3 hours ago",false,p.ink,p.muted,p.accent)
    }
   }
  }
  compose.onNodeWithTag("sera_thread_contact_name").assertTextEquals("Halima Aman")
  compose.onNodeWithTag("sera_thread_presence").assertTextEquals("3 hours ago")
 }
 @Test fun relativeActivityUsesElapsedTime(){
  assertEquals("Active now",seraRelativeActivity(45))
  assertEquals("5 minutes ago",seraRelativeActivity(300))
  assertEquals("1 hour ago",seraRelativeActivity(3600))
  assertEquals("3 hours ago",seraRelativeActivity(10800))
  assertEquals("2 days ago",seraRelativeActivity(172800))
  assertEquals("1 week ago",seraRelativeActivity(604800))
 }
}
