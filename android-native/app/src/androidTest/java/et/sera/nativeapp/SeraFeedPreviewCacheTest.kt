package et.sera.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class SeraFeedPreviewCacheTest {
 private fun post(id:String,audience:String="public",repost:String?=null)=SeraPost(id,"author","Title","Body",0,0,0,"post","2026-11-14T00:00:00Z",audience=audience,repostOf=repost)
 @Test fun onlyPublicOriginalTextIsCached(){
   val rows=seraFeedPreviewRows(listOf(post("a"),post("b","connections"),post("c","only_me"),post("d",repost="private-original")))
   assertEquals(listOf("a"),rows.map{it.id})
   assertEquals("Body",rows.single().body)
 }
 @Test fun cacheIsAccountIsolatedAndExpires(){
   val context=InstrumentationRegistry.getInstrumentation().targetContext
   val a=SeraFeedPreviewCache(context,"00000000-0000-4000-8000-000000000001")
   val b=SeraFeedPreviewCache(context,"00000000-0000-4000-8000-000000000002")
   try {
      a.write(listOf(post("a")),1000)
      assertEquals("a",a.read(1001).single().id)
      assertTrue(b.read(1001).isEmpty())
      assertTrue(a.read(1000+SERA_FEED_CACHE_TTL_MS+1).isEmpty())
      a.write(listOf(post("a")),2000)
      a.clear()
      assertTrue(a.read(2001).isEmpty())
   } finally {a.clear();b.clear()}
 }
}
