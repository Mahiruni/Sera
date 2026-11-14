package et.sera.nativeapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SeraFeedLayoutTest {
    @get:Rule val compose=createComposeRule()

    @Test fun duplicateTitleIsNotRenderedTwice(){
        assertEquals("Headline" to "Body",seraFeedCopyParts("Headline","Headline\n\nBody"))
        assertEquals("Headline" to "",seraFeedCopyParts("Headline","Headline"))
        assertEquals("Headline" to "",seraFeedCopyParts("Headline",""))
        assertEquals("Title" to "A separate body",seraFeedCopyParts("Title","A separate body"))
    }

    @Test fun longPostExpandsAndCollapsesBesideOneSeparator(){
        compose.setContent {
            MaterialTheme {
                var expanded by remember { mutableStateOf(false) }
                Column(Modifier.width(260.dp)) {
                    SeraFeedCopy("Professional update",List(12){"A useful idea for your network."}.joinToString(" "),expanded,{expanded=it})
                    SeraFeedDivider()
                }
            }
        }
        compose.onNodeWithTag("sera_feed_separator").assertExists().assertHeightIsEqualTo(1.dp)
        compose.onNodeWithTag("sera_feed_expand").assertExists().performClick()
        compose.onNodeWithText("Show less").assertExists().performClick()
        compose.onNodeWithText("Read more").assertExists()
    }

    @Test fun slowFeedOffersRetryInsteadOfPermanentSkeleton(){
        var retried=false
        compose.setContent{MaterialTheme{SeraFeedLoadingNotice{retried=true}}}
        compose.onNodeWithTag("sera_feed_slow").assertExists()
        compose.onNodeWithTag("sera_feed_retry").performClick()
        compose.runOnIdle{org.junit.Assert.assertTrue(retried)}
    }

    @Test fun livePostTextIsAvailableToAndroidAccessibility(){
        compose.setContent{MaterialTheme{SeraFeedCopy("","Actual post body",false,{},postId="qa-post")}}
        compose.onNodeWithTag("sera_feed_text_qa-post").assertIsDisplayed().assertTextEquals("Actual post body")
    }
    @Test fun lateEnrichmentCannotOverwriteNewGeneration(){
        val store=SeraFeedEnrichment()
        val first=store.begin()
        val row=SeraPost("post","member","","Post body",0,0,0,"post","2026-11-14T00:00:00Z")
        store.publish(first,listOf(row))
        assertEquals(1,store.rows.value.size)
        val second=store.begin()
        store.publish(first,listOf(row))
        assertEquals(0,store.rows.value.size)
        store.publish(second,listOf(row.copy(author="Member")))
        assertEquals("Member",store.rows.value["post"]?.author)
    }
    @Test fun shortPostDoesNotShowReadMore(){
        compose.setContent{MaterialTheme{SeraFeedCopy("","A short update.",false,{})}}
        compose.onNodeWithTag("sera_feed_expand").assertDoesNotExist()
    }
}
