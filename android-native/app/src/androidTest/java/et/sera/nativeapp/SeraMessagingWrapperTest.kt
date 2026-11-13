package et.sera.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.assertTrue
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule

class SeraMessagingWrapperTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overlayKeepsSourcePageAndFillsBottom(){
        compose.setContent {
            MaterialTheme {
                var open by remember{mutableStateOf(true)}
                Box(Modifier.fillMaxSize().testTag("source-page")){
                    Text("Existing feed post",Modifier.testTag("source-post"))
                    if(open)SeraMessagingWrapper(Modifier.fillMaxSize(),seraChatPalette(false),overlayOnPage=true,onDismiss={open=false}){Text("Inbox content")}
                }
            }
        }
        compose.onNodeWithTag("source-post").assertExists()
        val root=compose.onNodeWithTag("source-page").fetchSemanticsNode().boundsInRoot
        val sheet=compose.onNodeWithContentDescription("Messages sheet",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue("The original page remains visible above the sheet",sheet.top>root.top)
        assertEquals(root.bottom,sheet.bottom,2f)
        assertEquals(root.width,sheet.width,2f)
        compose.onNodeWithText("Inbox content").assertExists()
    }

    @Test fun messagingOccupiesNinetyPercentWithKeyboardClosed() {
        assertEquals(.90f,seraMessagingHeightFraction(false),.001f)
    }
    @Test fun messagingUsesEntireUsableViewportWithKeyboardOpen() {
        assertEquals(1f,seraMessagingHeightFraction(true),.001f)
    }
    @Test fun dragMovesUpAndDownWithinBounds() {
        assertEquals(.8f,seraMessagingDragFraction(.9f,100f,1000f),.001f)
        assertEquals(1f,seraMessagingDragFraction(.9f,-500f,1000f),.001f)
        assertEquals(.6f,seraMessagingDragFraction(.9f,500f,1000f),.001f)
    }
    @Test fun releaseSnapsToNearestOrFlingDirection() {
        assertEquals(.6f,seraMessagingSnapFraction(.67f,0f,420f),.001f)
        assertEquals(.9f,seraMessagingSnapFraction(.83f,0f,420f),.001f)
        assertEquals(1f,seraMessagingSnapFraction(.92f,-600f,420f),.001f)
        assertEquals(.6f,seraMessagingSnapFraction(.83f,600f,420f),.001f)
    }
    @Test fun keyboardPreservesRestingPosition() {
        assertEquals(1f,seraMessagingHeightFraction(true,.6f),.001f)
        assertEquals(.6f,seraMessagingHeightFraction(false,.6f),.001f)
    }
}
