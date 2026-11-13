package et.sera.nativeapp
import android.content.Intent
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
class MessagingStandardsTest{
 @Test fun launchRoutesUseBoundIdentifiers(){
  val owner="11111111-1111-4111-8111-111111111111";val thread="22222222-2222-4222-8222-222222222222"
  val intent=Intent().putExtra("kind","message").putExtra("recipient_id",owner).putExtra("conversation_id",thread)
  assertEquals("message:$thread:$owner",seraLaunchDestination(intent))
  intent.putExtra("conversation_id","../admin")
  assertEquals("message",seraLaunchDestination(intent))
  assertNull(seraLaunchDestination(null))
 }
 @Test fun richMessageRetainsUnicodeAndOnlySafeLinks(){
  val text=seraRichMessage("Hi **team** @alex 😊 https://example.com. javascript:alert(1)",Color.Cyan)
  assertEquals("Hi team @alex 😊 https://example.com. javascript:alert(1)",text.text)
  assertEquals(1,text.getLinkAnnotations(0,text.length).size)
  assertTrue(text.spanStyles.isNotEmpty())
  val pending=seraRichMessage("https://example.com",Color.Cyan,false)
  assertTrue(pending.getLinkAnnotations(0,pending.length).isEmpty())
 }
}
