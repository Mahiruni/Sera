package et.sera.nativeapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeraSignatureDesignSystemTest {
 @get:Rule val compose=createComposeRule()
 @Test fun signatureCallRequiresConfirmation(){
  var count=0
  compose.setContent{
   var open by remember{mutableStateOf(false)}
   val colors=signatureColors(false)
   MaterialTheme{
    SignatureCallAction("Call Hana",colors){open=true}
    if(open)SignatureCallConfirmation("Start a voice call?","Do you want to call Hana?","No","Yes, call",colors,{open=false},{open=false;count++})
   }
  }
  compose.onNodeWithContentDescription("Call Hana").performClick()
  compose.runOnIdle{assertEquals(0,count)}
  compose.onNodeWithText("No").performClick()
  compose.runOnIdle{assertEquals(0,count)}
  compose.onNodeWithContentDescription("Call Hana").performClick()
  compose.onNodeWithText("Yes, call").performClick()
  compose.runOnIdle{assertEquals(1,count)}
 }
}
