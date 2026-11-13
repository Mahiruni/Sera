package et.sera.nativeapp
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class MessageLibraryProTest{
 @Test fun highlightsAllMatchesWithoutChangingOriginalText(){
  val body="SERA, sera, professional."
  val result=seraHighlightedMessage(body,"sera",Color.Yellow,Color.Black)
  assertEquals(body,result.text)
  assertEquals(listOf(0,6),result.spanStyles.map{it.start})
  assertEquals(listOf(4,10),result.spanStyles.map{it.end})
 }
 @Test fun editWindowIsBounded(){
  val now=Instant.parse("2026-11-13T12:00:00Z")
  assertTrue(seraCanEdit("2026-11-13T11:45:01Z",now))
  assertFalse(seraCanEdit("2026-11-13T11:45:00Z",now))
  assertFalse(seraCanEdit("2026-11-13T12:00:01Z",now))
 }
}
