package et.sera.nativeapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeraSignatureIconsTest {
 @get:Rule val compose=createComposeRule()
 @Test fun keyActionsUseSignaturePaths(){
   for(icon in listOf(Icons.Outlined.Call,Icons.Outlined.Send,Icons.Outlined.AttachFile,Icons.Outlined.Close)){
     assertNotNull("Missing signature glyph for ${icon.name}",seraSignatureKey(icon.name))
   }
   for(name in listOf("Home","PeopleOutline","Explore","WorkOutline","Inbox","Mic","MicOff","CallEnd","History","Search","MoreVert","EditNote","Image","EmojiEmotions","Reply","Favorite","Bookmark","DeleteOutline")){
     assertNotNull(name,seraSignatureKey(name))
   }
   assertNull(seraSignatureKey("UnknownSpecializedVector"))
 }
 @Test fun accessibleActionNamesArePreserved(){
   compose.setContent {
     MaterialTheme(colorScheme=seraColorScheme(false)){
       Row{Icon(Icons.Outlined.Call,"Voice call");Icon(Icons.Outlined.Send,"Send message")}
     }
   }
   compose.onNodeWithContentDescription("Voice call").assertExists()
   compose.onNodeWithContentDescription("Send message").assertExists()
 }
}
