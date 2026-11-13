package et.sera.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class MessagingLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shortMessagesStayCompactAndReceiptsRemainAccessible() {
        compose.setContent {
            val p=seraChatPalette(true)
            MaterialTheme(colorScheme=seraChatScheme(androidx.compose.material3.darkColorScheme(),p,true),typography=seraChatTypography(androidx.compose.material3.Typography())) {
                Column(Modifier.fillMaxSize().background(p.canvas).padding(12.dp).testTag("thread"),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    Text("Messages",fontSize=22.sp,color=Color.White)
                    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.CenterEnd){
                        SeraCompactMessageBubble(true,p.outgoing,Modifier.widthIn(max=260.dp).testTag("short")){
                            SeraRichMessageText("hi",p.onOutgoing,p.outgoingLink)
                            SeraCompactReceipt("21:15",true,false,false,Color.White.copy(alpha=.8f))
                        }
                    }
                    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.CenterEnd){
                        SeraCompactMessageBubble(true,p.outgoing,Modifier.widthIn(max=260.dp).testTag("long")){
                            SeraRichMessageText("A longer professional message that wraps naturally without filling the entire conversation width.",p.onOutgoing,p.outgoingLink)
                            SeraCompactReceipt("21:16",true,true,true,Color.White.copy(alpha=.8f))
                        }
                    }
                    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.CenterStart){
                        SeraCompactMessageBubble(false,p.incoming,Modifier.widthIn(max=260.dp).testTag("incoming")){
                            SeraRichMessageText("Sounds good!",p.ink,p.accent)
                            SeraCompactReceipt("21:17",false,false,false,Color.White.copy(alpha=.8f))
                        }
                    }
                }
            }
        }
        val short=compose.onNodeWithTag("short").fetchSemanticsNode().boundsInRoot.width
        val long=compose.onNodeWithTag("long").fetchSemanticsNode().boundsInRoot.width
        assertTrue("Short bubbles must not expand to the same width as long bubbles", short < long * .65f)
        compose.onNodeWithContentDescription("Sent").assertExists()
        // Read messages no longer display delivery ticks; the thread renders one Seen label.
        compose.onNodeWithContentDescription("Read").assertDoesNotExist()
        compose.onNodeWithContentDescription("Delivered").assertDoesNotExist()
        compose.onNodeWithText("21:16").assertExists()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(context.filesDir,"messaging-qa").apply{mkdirs()}
        File(dir,"compact-bubbles.png").outputStream().use{compose.onNodeWithTag("thread").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
