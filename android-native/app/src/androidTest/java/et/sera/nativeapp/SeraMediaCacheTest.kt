package et.sera.nativeapp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
class SeraMediaCacheTest{
 private val owner="00000000-0000-4000-8000-000000000121"
 @Test fun independentUsageAndClearing(){
  val store=SeraLocalCache.get(InstrumentationRegistry.getInstrumentation().targetContext)
  val now=System.currentTimeMillis()
  val previous=store.budgetMb()
  try{
   store.setBudgetMb(15);assertEquals(30,store.imageBudgetMb())
   assertTrue(store.write(owner,"feed","latest","text",now,60000,1))
   assertTrue(store.write(owner,"thumbnail","image","bytes",now,60000,120))
   assertTrue(store.textBytes(owner)>0);assertTrue(store.imageBytes(owner)>0)
   assertEquals(store.bytes(owner),store.textBytes(owner)+store.imageBytes(owner))
   store.clear(owner,"thumbnail");assertEquals(0L,store.imageBytes(owner));assertEquals("text",store.read(owner,"feed","latest",now))
   store.setBudgetMb(50);assertEquals(200,store.imageBudgetMb())
  }finally{store.clear(owner);store.setBudgetMb(previous)}
 }
 @Test fun stableKeysExcludeUntrustedUrls(){
  assertEquals("",seraThumbnailMediaId("https://example.com/photo"))
  val id="00000000-0000-4000-8000-000000000123"
  assertEquals(id,seraThumbnailMediaId("https://sera.hisabtech.com/api/post-media/"+id))
  assertNotEquals(seraThumbnailKey("p",id,"old"),seraThumbnailKey("p",id,"new"))
 }
}
