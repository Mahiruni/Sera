package et.sera.nativeapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SeraDockLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun dockUsesOnlySystemBottomInsetAndNavigates(){
        var insetPx=0
        compose.setContent {
            val density=LocalDensity.current
            insetPx=WindowInsets.navigationBars.getBottom(density)
            var active by remember{mutableStateOf("Home")}
            MaterialTheme {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter){
                    SeraFloatingDock(active,listOf("Home","Network","Search","Jobs","Inbox"),{active=it},inboxCount=3)
                }
            }
        }
        compose.onNodeWithTag("sera_dock_Home").assertIsSelected()
        compose.onNodeWithTag("sera_dock_surface").assertHeightIsEqualTo(58.dp)
        val root=compose.onNodeWithTag("sera_dock_root").fetchSemanticsNode().boundsInRoot
        val surface=compose.onNodeWithTag("sera_dock_surface").fetchSemanticsNode().boundsInRoot
        assertEquals(insetPx.toFloat(),root.bottom-surface.bottom,1f)
        compose.onNodeWithTag("sera_dock_Search").performClick().assertIsSelected()
        compose.onNodeWithTag("sera_dock_Inbox").assertExists().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Messages, 3 unread",substring=true).assertExists()
    }
}
