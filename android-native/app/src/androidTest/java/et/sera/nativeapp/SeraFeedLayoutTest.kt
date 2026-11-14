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
    @Test fun directFeedPublishesBaseRowsWithoutWaitingForDetails(){
        val row=SeraPost("qa-post","member","","Actual post body",0,0,0,"post","2026-11-14T00:00:00Z")
        val release=java.util.concurrent.CountDownLatch(1)
        val feed=SeraDirectFeed("",false,false,fetch={listOf(row) to ""},enrich={release.await(10,java.util.concurrent.TimeUnit.SECONDS);null})
        try{
            compose.setContent{MaterialTheme{
                val state by feed.state.collectAsState()
                LaunchedEffect(Unit){feed.start()}
                Column{state.rows.forEach{SeraFeedCopy(it.title,it.body,false,{},postId=it.id)}}
            }}
            compose.waitUntil(5000){feed.state.value.rows.size==1}
            compose.onNodeWithTag("sera_feed_text_qa-post").assertIsDisplayed().assertTextEquals("Actual post body")
            assertEquals(false,feed.state.value.refreshing)
            assertEquals(0,feed.state.value.details.size)
        }finally{release.countDown();feed.close()}
    }
    @Test fun supersededBaseRequestCannotOverwriteLatestRows(){
        val row=SeraPost("qa-post","member","","Actual post body",0,0,0,"post","2026-11-14T00:00:00Z")
        val entered=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        val calls=java.util.concurrent.atomic.AtomicInteger()
        val feed=SeraDirectFeed("",false,false,fetch={
            if(calls.incrementAndGet()==1){
                entered.countDown()
                // Simulate an HTTP call that completes after cancellation.
                while(release.count>0){try{release.await(100,java.util.concurrent.TimeUnit.MILLISECONDS)}catch(_:InterruptedException){}}
                listOf(row.copy(id="old")) to ""
            }else listOf(row.copy(id="new")) to ""
        },enrich={null})
        try{
            feed.start()
            org.junit.Assert.assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
            feed.refresh()
            compose.waitUntil(5000){feed.state.value.rows.firstOrNull()?.id=="new"}
            release.countDown()
            compose.waitForIdle()
            assertEquals("new",feed.state.value.rows.first().id)
        }finally{release.countDown();feed.close()}
    }
    @Test fun failedRefreshRetainsPostsAndRetriesFirstPage(){
        val row=SeraPost("qa-post","member","","Actual post body",0,0,0,"post","2026-11-14T00:00:00Z")
        val failing=java.util.concurrent.atomic.AtomicBoolean(false)
        val pages=java.util.concurrent.CopyOnWriteArrayList<Int>()
        val feed=SeraDirectFeed("",false,false,fetch={page->pages.add(page);if(failing.get())null to "HTTP 503" else listOf(row) to ""},enrich={null})
        try{
            feed.start();compose.waitUntil(5000){feed.state.value.rows.size==1}
            failing.set(true);feed.refresh();compose.waitUntil(5000){feed.state.value.error.isNotBlank()}
            assertEquals(1,feed.state.value.rows.size)
            failing.set(false);feed.retry();compose.waitUntil(5000){!feed.state.value.refreshing&&feed.state.value.error.isBlank()}
            assertEquals(0,pages.last())
        }finally{feed.close()}
    }
    @Test fun deniedAccessPurgesCachedFeed(){
        val row=SeraPost("qa-post","member","","Actual post body",0,0,0,"post","2026-11-14T00:00:00Z")
        val denied=java.util.concurrent.atomic.AtomicBoolean(false)
        val invalidated=java.util.concurrent.atomic.AtomicBoolean(false)
        val feed=SeraDirectFeed("",false,false,fetch={if(denied.get())null to "HTTP 403" else listOf(row) to ""},enrich={null},onAccessLost={invalidated.set(true)})
        try{
            feed.start();compose.waitUntil(5000){feed.state.value.rows.size==1}
            denied.set(true);feed.refresh();compose.waitUntil(5000){feed.state.value.error.isNotBlank()}
            assertEquals(0,feed.state.value.rows.size)
            assertEquals(true,invalidated.get())
        }finally{feed.close()}
    }
    @Test fun shortPostDoesNotShowReadMore(){
        compose.setContent{MaterialTheme{SeraFeedCopy("","A short update.",false,{})}}
        compose.onNodeWithTag("sera_feed_expand").assertDoesNotExist()
    }
}
