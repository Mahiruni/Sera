package et.sera.nativeapp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
class SeraExploreTest {
    @get:Rule val compose=createComposeRule()
    @Test fun allSixDestinationsAreAccessible(){
        var selected=""
        compose.setContent{MaterialTheme{SeraExploreGrid(onOpen={selected=it})}}
        for(key in listOf("hiring","opportunities","events","spaces","talent","communities")){
            compose.onNodeWithTag("sera_explore_"+key).assertIsDisplayed().performClick()
            compose.runOnIdle{assertEquals(key,selected)}
        }
    }
}
