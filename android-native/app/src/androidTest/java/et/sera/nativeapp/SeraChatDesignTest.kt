package et.sera.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Test

class SeraChatDesignTest {
 private fun contrast(a:Color,b:Color):Float {
  val l=a.luminance();val r=b.luminance()
  return (maxOf(l,r)+0.05f)/(minOf(l,r)+0.05f)
 }
 @Test fun textAndMetadataMeetContrastInBothThemes(){
  for(dark in listOf(false,true)){
   val p=seraChatPalette(dark)
   assertTrue(contrast(p.ink,p.incoming)>=4.5f)
   assertTrue(contrast(p.muted,p.incoming)>=4.5f)
   assertTrue(contrast(p.onOutgoing,p.outgoing)>=4.5f)
   assertTrue(contrast(p.outgoingMeta,p.outgoing)>=4.5f)
   assertTrue(contrast(p.outgoingLink,p.outgoing)>=4.5f)
   assertTrue(contrast(p.outgoingLink,p.outgoingQuote)>=4.5f)
   assertTrue(contrast(p.ink,p.quote)>=4.5f)
   assertTrue(contrast(p.muted,p.surface)>=4.5f)
   assertTrue(contrast(p.accent,p.selection)>=4.5f)
   assertTrue(contrast(p.accent,p.quote)>=4.5f)
  }
 }
 @Test fun messageTypeScaleKeepsReadableLineHeight(){
  val t=seraChatTypography(Typography())
  assertEquals(15.sp,t.bodyLarge.fontSize)
  assertEquals(24.sp,t.bodyLarge.lineHeight)
  assertEquals(11.sp,t.labelSmall.fontSize)
 }
}
